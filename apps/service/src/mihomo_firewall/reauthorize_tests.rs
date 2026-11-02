//! Pure descriptor/recovery tests: never open WFP or inspect/mutate host networking.
use super::*;

const OWNED: u64 = 0x0006_0000_0000_0123;
const PHYSICAL: u64 = 0x0006_0000_0000_0456;
const FOREIGN: u64 = 0x0006_0000_0000_0789;
const SID: &str = "S-1-5-21-42";

fn journal(version: u32) -> Journal {
    Journal {
        version,
        sid: SID.into(),
        sublayer: Uuid::from_u128(0x00112233_4455_6677_8899_aabbccddeeff),
        native: None,
        device: None,
    }
}

fn key(g: GUID) -> (u32, u16, u16, [u8; 8]) {
    (g.data1, g.data2, g.data3, g.data4)
}

fn ale(family: u8) -> GUID {
    if family == 0 {
        FWPM_LAYER_ALE_AUTH_CONNECT_V4
    } else {
        FWPM_LAYER_ALE_AUTH_CONNECT_V6
    }
}

#[derive(Clone, Copy, Debug)]
enum Value {
    Empty,
    Luid(u64),
}

#[derive(Clone, Copy, Debug)]
struct Packet {
    flags: u32,
    next_hop: Value,
    arrival: Value,
}

fn owned_return() -> Packet {
    Packet {
        flags: FWP_CONDITION_FLAG_IS_REAUTHORIZE,
        next_hop: Value::Empty,
        arrival: Value::Luid(OWNED),
    }
}

// This is only a typed predicate evaluator for the actual installed TUN
// descriptors. It neither builds a substitute policy nor emulates kernel WFP.
// All descriptor pointers are inspected synchronously inside with_tun_filters.
fn matches_descriptor(f: &FWPM_FILTER0, packet: Packet) -> bool {
    let conditions =
        unsafe { std::slice::from_raw_parts(f.filterCondition, f.numFilterConditions as usize) };
    conditions.iter().all(|c| {
        if key(c.fieldKey) == key(FWPM_CONDITION_FLAGS) {
            assert_eq!(c.conditionValue.r#type, FWP_UINT32);
            assert_eq!(c.matchType, FWP_MATCH_FLAGS_ALL_SET);
            let expected = unsafe { c.conditionValue.Anonymous.uint32 };
            packet.flags & expected == expected
        } else {
            let actual = if key(c.fieldKey) == key(FWPM_CONDITION_IP_NEXTHOP_INTERFACE) {
                packet.next_hop
            } else if key(c.fieldKey) == key(FWPM_CONDITION_IP_ARRIVAL_INTERFACE) {
                packet.arrival
            } else {
                panic!("unexpected field in TUN descriptor");
            };
            assert_eq!(c.matchType, FWP_MATCH_EQUAL);
            match c.conditionValue.r#type {
                FWP_EMPTY => matches!(actual, Value::Empty),
                FWP_UINT64 => {
                    let value = unsafe { c.conditionValue.Anonymous.uint64 };
                    assert!(!value.is_null());
                    matches!(actual, Value::Luid(n) if n == unsafe { *value })
                }
                _ => panic!("unexpected value type in TUN descriptor"),
            }
        }
    })
}

fn tun_permission(family: u8, packet: Packet, only_role: Option<u8>) -> bool {
    let j = journal(JOURNAL_VERSION);
    let mut luid = OWNED;
    let mut permitted = false;
    let mut seen = 0;
    with_tun_filters(&mut luid, |role, conditions| {
        if only_role.is_none() || only_role == Some(role) {
            seen += 1;
            let f = filter_descriptor(&j, family, role, conditions);
            assert_eq!(key(f.layerKey), key(ale(family)));
            assert_eq!(f.action.r#type, FWP_ACTION_PERMIT);
            assert_eq!(f.flags, 0, "TUN permits must stay soft and ephemeral");
            assert_eq!(f.weight.r#type, FWP_UINT8);
            assert_eq!(unsafe { f.weight.Anonymous.uint8 }, 15);
            permitted |= matches_descriptor(&f, packet);
        }
        Ok(())
    })
    .unwrap();
    assert!(
        seen > 0,
        "requested role absent from actual TUN installation"
    );
    // Model only a non-core/non-loopback/non-DHCP flow. The actual unchanged
    // catch-all block denies it if no TUN descriptor permits it.
    let block = filter_descriptor(&j, family, 0, &mut []);
    assert_eq!(key(block.layerKey), key(ale(family)));
    assert_eq!(block.action.r#type, FWP_ACTION_BLOCK);
    assert_eq!(block.numFilterConditions, 0);
    permitted
}

#[test]
fn valid_owned_tun_return_v4_is_permitted() {
    assert!(tun_permission(0, owned_return(), None));
}

#[test]
fn valid_owned_tun_return_v6_is_permitted() {
    assert!(tun_permission(1, owned_return(), None));
}

#[test]
fn role7_conditions_are_exact_soft_ephemeral_and_installed_after_role4() {
    for family in 0..2 {
        let j = journal(JOURNAL_VERSION);
        let mut luid = OWNED;
        let mut roles = Vec::new();
        with_tun_filters(&mut luid, |role, conditions| {
            roles.push(role);
            if role == 7 {
                let f = filter_descriptor(&j, family, role, conditions);
                assert_eq!(key(f.layerKey), key(ale(family)));
                assert_eq!(key(f.filterKey), key(j.key(family, 7)));
                assert_ne!(key(f.filterKey), key(j.key(family, 4)));
                assert_eq!(key(f.subLayerKey), key(j.layer_key()));
                assert_eq!(f.action.r#type, FWP_ACTION_PERMIT);
                assert_eq!(f.flags, 0);
                assert_eq!(f.weight.r#type, FWP_UINT8);
                assert_eq!(unsafe { f.weight.Anonymous.uint8 }, 15);
                assert_eq!(f.numFilterConditions, 3);
                assert_eq!(f.filterCondition, conditions.as_mut_ptr());
                assert_eq!(key(conditions[0].fieldKey), key(FWPM_CONDITION_FLAGS));
                assert_eq!(conditions[0].matchType, FWP_MATCH_FLAGS_ALL_SET);
                assert_eq!(conditions[0].conditionValue.r#type, FWP_UINT32);
                assert_eq!(
                    unsafe { conditions[0].conditionValue.Anonymous.uint32 },
                    FWP_CONDITION_FLAG_IS_REAUTHORIZE
                );
                assert_eq!(
                    key(conditions[1].fieldKey),
                    key(FWPM_CONDITION_IP_NEXTHOP_INTERFACE)
                );
                assert_eq!(conditions[1].matchType, FWP_MATCH_EQUAL);
                assert_eq!(conditions[1].conditionValue.r#type, FWP_EMPTY);
                assert_eq!(
                    key(conditions[2].fieldKey),
                    key(FWPM_CONDITION_IP_ARRIVAL_INTERFACE)
                );
                assert_eq!(conditions[2].matchType, FWP_MATCH_EQUAL);
                assert_eq!(conditions[2].conditionValue.r#type, FWP_UINT64);
                assert_eq!(
                    unsafe { *conditions[2].conditionValue.Anonymous.uint64 },
                    OWNED
                );
            }
            Ok(())
        })
        .unwrap();
        assert_eq!(roles, [4, 7]);
    }
}

#[test]
fn only_boot_roles_use_transport_layers() {
    let j = journal(3);
    for family in 0..2 {
        for role in 0..8 {
            let f = filter_descriptor(&j, family, role, &mut []);
            let expected = match (role, family) {
                (5 | 6, 0) => FWPM_LAYER_OUTBOUND_TRANSPORT_V4,
                (5 | 6, _) => FWPM_LAYER_OUTBOUND_TRANSPORT_V6,
                (_, _) => ale(family),
            };
            assert_eq!(
                key(f.layerKey),
                key(expected),
                "role={role} family={family}"
            );
        }
    }
}

#[test]
fn role7_rejects_every_nonempty_next_hop_and_nonowned_or_empty_arrival() {
    let valid = owned_return();
    let negatives = [
        Packet {
            next_hop: Value::Luid(PHYSICAL),
            ..valid
        },
        Packet {
            next_hop: Value::Luid(OWNED),
            ..valid
        },
        Packet {
            next_hop: Value::Luid(0),
            ..valid
        },
        Packet {
            arrival: Value::Luid(PHYSICAL),
            ..valid
        },
        Packet {
            arrival: Value::Luid(FOREIGN),
            ..valid
        },
        Packet {
            arrival: Value::Luid(0),
            ..valid
        },
        Packet {
            arrival: Value::Empty,
            ..valid
        },
        Packet { flags: 0, ..valid },
    ];
    for family in 0..2 {
        for packet in negatives {
            assert!(!tun_permission(family, packet, Some(7)), "{packet:?}");
        }
        assert!(tun_permission(family, valid, Some(7)));
        assert!(tun_permission(
            family,
            Packet {
                flags: valid.flags | FWP_CONDITION_FLAG_IS_LOOPBACK,
                ..valid
            },
            Some(7)
        ));
    }
}

#[test]
fn combined_tun_permissions_still_deny_physical_and_unknown_returns() {
    for family in 0..2 {
        let valid = owned_return();
        for packet in [
            Packet {
                next_hop: Value::Luid(PHYSICAL),
                ..valid
            },
            Packet {
                arrival: Value::Luid(PHYSICAL),
                ..valid
            },
            Packet {
                arrival: Value::Luid(FOREIGN),
                ..valid
            },
            Packet {
                arrival: Value::Empty,
                ..valid
            },
            Packet { flags: 0, ..valid },
        ] {
            assert!(!tun_permission(family, packet, None), "{packet:?}");
        }
    }
}

#[test]
fn role4_outbound_condition_and_permission_stay_unchanged() {
    for family in 0..2 {
        let j = journal(JOURNAL_VERSION);
        let mut luid = OWNED;
        with_tun_filters(&mut luid, |role, conditions| {
            if role == 4 {
                assert_eq!(conditions.len(), 1);
                assert_eq!(
                    key(conditions[0].fieldKey),
                    key(FWPM_CONDITION_IP_NEXTHOP_INTERFACE)
                );
                assert_eq!(conditions[0].matchType, FWP_MATCH_EQUAL);
                assert_eq!(conditions[0].conditionValue.r#type, FWP_UINT64);
                assert_eq!(
                    unsafe { *conditions[0].conditionValue.Anonymous.uint64 },
                    OWNED
                );
                let f = filter_descriptor(&j, family, role, conditions);
                assert_eq!(key(f.filterKey), key(j.key(family, 4)));
            }
            Ok(())
        })
        .unwrap();
        let outbound = Packet {
            flags: 0,
            next_hop: Value::Luid(OWNED),
            arrival: Value::Empty,
        };
        assert!(tun_permission(family, outbound, Some(4)));
        assert!(tun_permission(family, outbound, None));
        assert!(!tun_permission(family, owned_return(), Some(4)));
        assert!(!tun_permission(
            family,
            Packet {
                next_hop: Value::Luid(PHYSICAL),
                ..outbound
            },
            None
        ));
    }
}

#[test]
fn existing_physical_blocks_and_boot_flags_are_unchanged() {
    let j = journal(JOURNAL_VERSION);
    for family in 0..2 {
        for role in [0, 5] {
            let f = filter_descriptor(&j, family, role, &mut []);
            assert_eq!(f.action.r#type, FWP_ACTION_BLOCK);
            assert_eq!(
                f.flags,
                if role == 0 {
                    FWPM_FILTER_FLAG_PERSISTENT
                } else {
                    FWPM_FILTER_FLAG_BOOTTIME
                }
            );
            assert_eq!(f.weight.r#type, FWP_UINT8);
            assert_eq!(unsafe { f.weight.Anonymous.uint8 }, 0);
            assert_eq!(f.numFilterConditions, 0);
        }
    }
}

// Literal GUIDs independently pin the legacy XOR namespace, family and role
// ordering. Version-specific slicing must not use the implementation's ROLES.
const EXPECTED_KEYS: [u128; 16] = [
    0x80112233_4455_6677_8899_aabbccddeeff,
    0x80112233_4455_6677_8899_aabbccddeefe,
    0x80112233_4455_6677_8899_aabbccddeefd,
    0x80112233_4455_6677_8899_aabbccddeefc,
    0x80112233_4455_6677_8899_aabbccddeefb,
    0x80112233_4455_6677_8899_aabbccddeefa,
    0x80112233_4455_6677_8899_aabbccddeef9,
    0x80112233_4455_6677_8899_aabbccddeef8,
    0x80112233_4455_6677_8899_aabbccddefff,
    0x80112233_4455_6677_8899_aabbccddeffe,
    0x80112233_4455_6677_8899_aabbccddeffd,
    0x80112233_4455_6677_8899_aabbccddeffc,
    0x80112233_4455_6677_8899_aabbccddeffb,
    0x80112233_4455_6677_8899_aabbccddeffa,
    0x80112233_4455_6677_8899_aabbccddeff9,
    0x80112233_4455_6677_8899_aabbccddeff8,
];

fn assert_recovery_plan(version: u32, roles: usize) {
    let j = journal(version);
    let plan = j.recovery_plan().unwrap();
    let expected: Vec<_> = EXPECTED_KEYS[..roles]
        .iter()
        .chain(EXPECTED_KEYS[8..8 + roles].iter())
        .map(|n| key(GUID::from_u128(*n)))
        .collect();
    assert_eq!(
        plan.filters.iter().copied().map(key).collect::<Vec<_>>(),
        expected
    );
    assert_eq!(key(plan.sublayer), key(j.layer_key()));
    assert_eq!(
        plan.provider.map(key),
        (version >= 2).then(|| key(j.layer_key()))
    );
    let unique: std::collections::HashSet<_> = plan.filters.iter().copied().map(key).collect();
    assert_eq!(unique.len(), 2 * roles);
    assert!(!unique.contains(&key(j.layer_key())));
    assert!(unique.contains(&key(j.key(0, 4))));
    assert!(unique.contains(&key(j.key(1, 4))));
}

#[test]
fn v1_recovery_deletes_only_its_ten_exact_keys_and_no_provider() {
    assert_recovery_plan(1, 5);
}

#[test]
fn v2_recovery_deletes_only_its_fourteen_exact_keys_and_provider() {
    assert_recovery_plan(2, 7);
}

#[test]
fn v3_recovery_deletes_all_sixteen_exact_keys_including_role7_and_provider() {
    assert_recovery_plan(3, 8);
}

#[test]
fn all_supported_versions_keep_same_sid_authorization() {
    for version in [1, 2, 3] {
        let j = journal(version);
        assert_eq!(j.authorize(SID), Ok(()));
        assert_eq!(j.authorize("S-1-5-21-43"), Err(OWNER.into()));
    }
}

#[test]
fn unsupported_versions_have_no_authorization_or_recovery_plan() {
    for version in [0, 4, u32::MAX] {
        let j = journal(version);
        assert_eq!(j.authorize(SID), Err(OWNER.into()));
        assert!(matches!(j.recovery_plan(), Err(e) if e == OWNER));
    }
}

#[test]
fn newly_written_journals_use_version3() {
    assert_eq!(JOURNAL_VERSION, 3);
    assert_eq!(ROLES, 8);
}

#[test]
fn role4_install_failure_does_not_attempt_role7() {
    let mut luid = OWNED;
    let mut attempted = Vec::new();
    let result = with_tun_filters(&mut luid, |role, _| {
        attempted.push(role);
        Err("ROLE4_FAILED".into())
    });
    assert_eq!(attempted, [4]);
    assert_eq!(result, Err("ROLE4_FAILED".into()));
}

#[test]
fn role7_install_failure_is_reported_to_the_enclosing_transaction() {
    let mut luid = OWNED;
    let mut attempted = Vec::new();
    let result = with_tun_filters(&mut luid, |role, _| {
        attempted.push(role);
        if role == 7 {
            Err("ROLE7_FAILED".into())
        } else {
            Ok(())
        }
    });
    assert_eq!(attempted, [4, 7]);
    assert_eq!(result, Err("ROLE7_FAILED".into()));
}
