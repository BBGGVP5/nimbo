import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { api, type RoutingProfile, type RoutingProfileSummary } from "../lib/api";
import { fillTemplate, useMessages, type Messages } from "../lib/i18n";
import { notifyError, notifyInfo } from "../lib/notify";

type RoutingEditorMode = "create" | "edit";

const DOMAIN_STRATEGIES = ["AsIs", "IPIfNonMatch", "IPOnDemand"] as const;
const RULE_ORDERS = [
  "block-proxy-direct",
  "block-direct-proxy",
  "proxy-block-direct",
  "proxy-direct-block",
  "direct-block-proxy",
  "direct-proxy-block",
] as const;

export function Routing() {
  const m = useMessages();
  const [profiles, setProfiles] = useState<RoutingProfileSummary[]>([]);
  const [activeId, setActiveId] = useState<string>("global");
  const [refreshing, setRefreshing] = useState(false);
  const [editorProfile, setEditorProfile] = useState<RoutingProfile | null>(null);
  const [editorMode, setEditorMode] = useState<RoutingEditorMode>("edit");

  const load = useCallback(async () => {
    try {
      const result = await api.listRoutingProfiles();
      setProfiles(result.profiles);
      setActiveId(result.active);
    } catch (e) {
      notifyError(String(e));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onPick = async (profile: RoutingProfileSummary) => {
    if (profile.id === activeId) return;
    try {
      const result = await api.setActiveRoutingProfile(profile.id);
      setProfiles(result.profiles);
      setActiveId(result.active);
      void api.reapplyRuntimeConfig().catch(() => undefined);
      notifyInfo(m.routing.profileActivated.replace("{name}", profile.name));
    } catch (e) {
      notifyError(String(e));
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    try {
      await load();
    } finally {
      setRefreshing(false);
    }
  };

  const onOpenFolder = async () => {
    try {
      await api.openRoutingFolder();
    } catch (e) {
      notifyError(String(e));
    }
  };

  const onCreateProfile = () => {
    setEditorMode("create");
    setEditorProfile(makeNewRoutingProfile(profiles, m));
  };

  const onEditProfile = async (profile: RoutingProfileSummary) => {
    try {
      const detail = await api.getRoutingProfile(profile.id);
      setEditorMode("edit");
      setEditorProfile(detail);
    } catch (e) {
      notifyError(String(e));
    }
  };

  const onSaveProfile = async (profile: RoutingProfile) => {
    const saved = await api.updateRoutingProfile(profile);
    await load();
    setEditorProfile(null);
    if (saved.id === activeId) {
      void api.reapplyRuntimeConfig().catch(() => undefined);
    }
    notifyInfo(m.routing.profileSaved.replace("{name}", saved.name));
  };

  const onDeleteProfile = async (profile: RoutingProfile) => {
    const result = await api.deleteRoutingProfile(profile.id);
    setProfiles(result.profiles);
    setActiveId(result.active);
    setEditorProfile(null);
    if (profile.id === activeId) {
      void api.reapplyRuntimeConfig().catch(() => undefined);
    }
    notifyInfo(m.routing.profileDeleted.replace("{name}", profile.name));
  };

  const onCopyProfile = async (profile: RoutingProfileSummary) => {
    try {
      const json = await api.exportRoutingProfile(profile.id);
      await api.writeClipboardText(json);
      notifyInfo(m.routing.profileCopied.replace("{name}", profile.name));
    } catch (e) {
      notifyError(String(e));
    }
  };

  const onDuplicateProfile = async (profile: RoutingProfileSummary) => {
    try {
      const original = await api.getRoutingProfile(profile.id);
      const copy: RoutingProfile = {
        ...original,
        id: makeCopyProfileId(original.id || profile.id),
        name: makeCopyProfileName(original.name || profile.name, profiles, m.routing.copySuffix),
        builtin: false,
        last_updated_at: null,
      };
      await api.updateRoutingProfile(copy);
      await load();
      notifyInfo(m.routing.profileDuplicated.replace("{name}", copy.name));
    } catch (e) {
      notifyError(String(e));
    }
  };

  return (
    <div className="routing-page h-full overflow-auto">
      <div className="routing-header">
        <div className="routing-header-left">
          <h1 className="page-title">{m.routing.title}</h1>
          <div className="routing-subtitle">
            {m.routing.profileCount.replace("{count}", String(profiles.length))}
          </div>
        </div>
        <div className="routing-header-actions">
          <RoutingActionButton title={m.routing.openFolder} onClick={() => void onOpenFolder()}>
            <FolderIcon />
          </RoutingActionButton>
          <RoutingActionButton
            title={m.routing.refresh}
            onClick={() => void onRefresh()}
            spinning={refreshing}
          >
            <RefreshIcon spinning={refreshing} />
          </RoutingActionButton>
          <button
            type="button"
            className="routing-add-button"
            title={m.routing.addCustom}
            aria-label={m.routing.addCustom}
            onClick={onCreateProfile}
          >
            <PlusIcon />
          </button>
        </div>
      </div>

      <div className="routing-list">
        {profiles.map((profile) => (
          <RoutingCard
            key={profile.id}
            profile={profile}
            active={profile.id === activeId}
            labels={m}
            onActivate={() => void onPick(profile)}
            onCopy={() => void onCopyProfile(profile)}
            onDuplicate={() => void onDuplicateProfile(profile)}
            onEdit={() => void onEditProfile(profile)}
            onDelete={() => void onDeleteProfileFromCard(profile, onDeleteProfile, m)}
          />
        ))}
      </div>

      {editorProfile && (
        <RoutingEditorDialog
          mode={editorMode}
          profile={editorProfile}
          labels={m}
          onSave={onSaveProfile}
          onDelete={onDeleteProfile}
          onClose={() => setEditorProfile(null)}
        />
      )}
    </div>
  );
}

function RoutingCard({
  profile,
  active,
  labels,
  onActivate,
  onCopy,
  onDuplicate,
  onEdit,
  onDelete,
}: {
  profile: RoutingProfileSummary;
  active: boolean;
  labels: Messages;
  onActivate: () => void;
  onCopy: () => void;
  onDuplicate: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <div
      className={["routing-card", active ? "routing-card-active" : ""].join(" ")}
      role="button"
      tabIndex={0}
      onClick={onActivate}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onActivate();
        }
      }}
    >
      <div className="routing-card-body">
        <div className="routing-card-head">
          <div className="routing-card-name">{profile.name}</div>
          {profile.builtin && (
            <span className="routing-badge-builtin">{labels.routing.builtin}</span>
          )}
        </div>
        <div className="routing-card-description">{profile.description || labels.routing.noDescription}</div>
        <div className="routing-card-meta">
          <span className="routing-card-meta-pill">
            {profile.rules_count} {labels.routing.rulesLabel}
          </span>
          <span className="routing-card-meta-sep">·</span>
          <span className="routing-card-meta-text">{profile.action}</span>
          <span className="routing-card-meta-sep">·</span>
          <span className="routing-card-meta-text">{profile.strategy}</span>
        </div>
      </div>
      <div className="routing-card-actions">
        {active && (
          <span className="routing-active-pill">
            {labels.routing.activeLabel}
          </span>
        )}
        <button
          type="button"
          className="routing-card-icon-btn"
          aria-label={labels.routing.copy}
          title={labels.routing.copy}
          onClick={(e) => {
            e.stopPropagation();
            onCopy();
          }}
        >
          <ClipboardIcon />
        </button>
        <button
          type="button"
          className="routing-card-icon-btn"
          aria-label={labels.routing.duplicate}
          title={labels.routing.duplicate}
          onClick={(e) => {
            e.stopPropagation();
            onDuplicate();
          }}
        >
          <DuplicateIcon />
        </button>
        <button
          type="button"
          className="routing-card-icon-btn"
          aria-label={labels.routing.editProfile}
          title={labels.routing.editProfile}
          onClick={(e) => {
            e.stopPropagation();
            onEdit();
          }}
        >
          <EditIcon />
        </button>
        <button
          type="button"
          className="routing-card-icon-btn routing-card-danger-btn"
          aria-label={labels.routing.delete}
          title={labels.routing.delete}
          onClick={(e) => {
            e.stopPropagation();
            onDelete();
          }}
        >
          <TrashIcon />
        </button>
      </div>
    </div>
  );
}

function RoutingEditorDialog({
  mode,
  profile,
  labels,
  onSave,
  onDelete,
  onClose,
}: {
  mode: RoutingEditorMode;
  profile: RoutingProfile;
  labels: Messages;
  onSave: (profile: RoutingProfile) => Promise<void>;
  onDelete: (profile: RoutingProfile) => Promise<void>;
  onClose: () => void;
}) {
  const [draft, setDraft] = useState(profile);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    setDraft(profile);
    setSaving(false);
    setDeleting(false);
    setConfirmDelete(false);
  }, [profile]);

  const setField = <K extends keyof RoutingProfile>(key: K, value: RoutingProfile[K]) => {
    setDraft((current) => ({ ...current, [key]: value }));
  };

  const save = async () => {
    const next = normalizeProfileForSave(draft);
    if (!next.id) {
      notifyError(labels.routing.profileIdRequired);
      return;
    }
    if (!next.name) {
      notifyError(labels.routing.profileNameRequired);
      return;
    }

    setSaving(true);
    try {
      await onSave(next);
    } catch (e) {
      notifyError(String(e));
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    setDeleting(true);
    try {
      await onDelete(draft);
    } catch (e) {
      notifyError(String(e));
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="routing-editor-backdrop" role="presentation" onClick={onClose}>
      <div
        className="routing-editor-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="routing-editor-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="routing-editor-header">
          <div>
            <h2 id="routing-editor-title" className="routing-editor-title">
              {mode === "create" ? labels.routing.newProfile : labels.routing.editProfile}
            </h2>
            <div className="routing-editor-subtitle">
              {draft.builtin ? labels.routing.builtin : labels.routing.customProfile}
            </div>
          </div>
          <button
            type="button"
            className="routing-card-icon-btn"
            title={labels.common.close}
            aria-label={labels.common.close}
            onClick={onClose}
          >
            <CloseIcon />
          </button>
        </div>

        <div className="routing-editor-scroll">
          <section className="routing-editor-section">
            <div className="routing-editor-grid">
              <TextField
                label={labels.routing.profileName}
                value={draft.name}
                onChange={(value) => setField("name", value)}
              />
              <TextField
                label={labels.routing.profileId}
                value={draft.id}
                readOnly={mode === "edit"}
                onChange={(value) => setField("id", slugifyProfileId(value))}
              />
            </div>
            <label className="routing-editor-field routing-editor-field-full">
              <span>{labels.common.description}</span>
              <textarea
                className="routing-editor-input routing-editor-textarea"
                value={draft.description}
                onChange={(e) => setField("description", e.target.value)}
              />
            </label>
          </section>

          <section className="routing-editor-section">
            <div className="routing-editor-grid">
              <SelectField
                label={labels.routing.domainStrategy}
                value={draft.domain_strategy}
                options={DOMAIN_STRATEGIES}
                onChange={(value) => setField("domain_strategy", value)}
              />
              <SelectField
                label={labels.routing.ruleOrder}
                value={draft.rule_order}
                options={RULE_ORDERS}
                onChange={(value) => setField("rule_order", value)}
              />
            </div>
            <div className="routing-editor-switch-grid">
              <CheckboxField
                label={labels.settings.globalProxy}
                checked={draft.global_proxy}
                onChange={(value) => setField("global_proxy", value)}
              />
              <CheckboxField
                label={labels.settings.bypassPrivateIp}
                checked={draft.bypass_local_ip}
                onChange={(value) => setField("bypass_local_ip", value)}
              />
              <CheckboxField
                label="FakeDNS"
                checked={draft.fake_dns}
                onChange={(value) => setField("fake_dns", value)}
              />
            </div>
            <div className="routing-editor-grid">
              <TextField
                label={labels.routing.remoteDnsIp}
                value={draft.remote_dns_ip}
                onChange={(value) => setField("remote_dns_ip", value)}
              />
              <TextField
                label={labels.routing.localDnsIp}
                value={draft.local_dns_ip}
                onChange={(value) => setField("local_dns_ip", value)}
              />
              <TextField
                label={labels.routing.remoteDnsUrl}
                value={draft.remote_dns_url}
                onChange={(value) => setField("remote_dns_url", value)}
              />
              <TextField
                label={labels.routing.updateIntervalHours}
                type="number"
                value={String(draft.update_interval_hours)}
                onChange={(value) => setField("update_interval_hours", clampHours(value))}
              />
            </div>
            <TextField
              label={labels.routing.sourceUrl}
              value={draft.source_url ?? ""}
              onChange={(value) => setField("source_url", value.trim() ? value : null)}
            />
          </section>

          <section className="routing-editor-section">
            <div className="routing-editor-section-title">{labels.routing.rules}</div>
            <div className="routing-editor-rule-grid">
              <ListField
                label={labels.routing.directDomains}
                value={draft.direct_domains}
                placeholder={"domain:example.com\ngeosite:private"}
                onChange={(value) => setField("direct_domains", value)}
              />
              <ListField
                label={labels.routing.directIps}
                value={draft.direct_ips}
                placeholder={"geoip:private\n192.168.0.0/16"}
                onChange={(value) => setField("direct_ips", value)}
              />
              <ListField
                label={labels.routing.proxyDomains}
                value={draft.proxy_domains}
                placeholder={"domain:openai.com\nkeyword:chatgpt"}
                onChange={(value) => setField("proxy_domains", value)}
              />
              <ListField
                label={labels.routing.proxyIps}
                value={draft.proxy_ips}
                placeholder={"1.1.1.1\n203.0.113.0/24"}
                onChange={(value) => setField("proxy_ips", value)}
              />
              <ListField
                label={labels.routing.blockDomains}
                value={draft.block_domains}
                placeholder="geosite:category-ads-all"
                onChange={(value) => setField("block_domains", value)}
              />
              <ListField
                label={labels.routing.blockIps}
                value={draft.block_ips}
                placeholder="geoip:private"
                onChange={(value) => setField("block_ips", value)}
              />
            </div>
          </section>
        </div>

        <div className="routing-editor-footer">
          <div className="routing-editor-footer-left">
            {mode === "edit" && (
              confirmDelete ? (
                <div className="routing-delete-confirm">
                  <span>{fillTemplate(labels.routing.deleteConfirm, { name: draft.name })}</span>
                  <button
                    type="button"
                    className="routing-editor-danger"
                    disabled={deleting || saving}
                    onClick={() => void remove()}
                  >
                    {deleting ? labels.common.savingProgress : labels.routing.delete}
                  </button>
                  <button
                    type="button"
                    className="routing-editor-secondary"
                    disabled={deleting}
                    onClick={() => setConfirmDelete(false)}
                  >
                    {labels.common.cancel}
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  className="routing-editor-danger"
                  disabled={saving}
                  onClick={() => setConfirmDelete(true)}
                >
                  <TrashIcon />
                  <span>{labels.routing.delete}</span>
                </button>
              )
            )}
          </div>
          <div className="routing-editor-actions">
            <button
              type="button"
              className="routing-editor-secondary"
              disabled={saving || deleting}
              onClick={onClose}
            >
              {labels.common.cancel}
            </button>
            <button
              type="button"
              className="routing-editor-primary"
              disabled={saving || deleting}
              onClick={() => void save()}
            >
              {saving ? labels.common.saving : labels.common.save}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function TextField({
  label,
  value,
  onChange,
  readOnly = false,
  type = "text",
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
  type?: "text" | "number";
}) {
  return (
    <label className="routing-editor-field">
      <span>{label}</span>
      <input
        className="routing-editor-input"
        value={value}
        type={type}
        readOnly={readOnly}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}

function SelectField<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: readonly T[];
  onChange: (value: T) => void;
}) {
  return (
    <label className="routing-editor-field">
      <span>{label}</span>
      <select
        className="routing-editor-input"
        value={value}
        onChange={(e) => onChange(e.target.value as T)}
      >
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  );
}

function CheckboxField({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <label className="routing-editor-check">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span>{label}</span>
    </label>
  );
}

function ListField({
  label,
  value,
  placeholder,
  onChange,
}: {
  label: string;
  value: string[];
  placeholder: string;
  onChange: (value: string[]) => void;
}) {
  return (
    <label className="routing-editor-field">
      <span>{label}</span>
      <textarea
        className="routing-editor-input routing-editor-list"
        value={value.join("\n")}
        placeholder={placeholder}
        onChange={(e) => onChange(parseListInput(e.target.value))}
        spellCheck={false}
      />
    </label>
  );
}

async function onDeleteProfileFromCard(
  profile: RoutingProfileSummary,
  onDelete: (profile: RoutingProfile) => Promise<void>,
  labels: Messages,
) {
  try {
    if (!window.confirm(fillTemplate(labels.routing.deleteConfirm, { name: profile.name }))) {
      return;
    }
    const detail = await api.getRoutingProfile(profile.id);
    await onDelete(detail);
  } catch (e) {
    notifyError(String(e));
  }
}

function makeNewRoutingProfile(
  profiles: Array<{ id: string; name: string }>,
  labels: Messages,
): RoutingProfile {
  return {
    id: makeUniqueProfileId("custom", profiles),
    name: makeUniqueProfileName(labels.routing.newProfileName, profiles),
    description: "",
    builtin: false,
    domain_strategy: "IPIfNonMatch",
    rule_order: "block-proxy-direct",
    global_proxy: true,
    bypass_local_ip: true,
    fake_dns: false,
    remote_dns_ip: "1.1.1.1",
    local_dns_ip: "8.8.8.8",
    remote_dns_url: "https://1.1.1.1/dns-query",
    direct_domains: [],
    direct_ips: [],
    proxy_domains: [],
    proxy_ips: [],
    block_domains: [],
    block_ips: [],
    source_url: null,
    update_interval_hours: 24,
    last_updated_at: null,
  };
}

function normalizeProfileForSave(profile: RoutingProfile): RoutingProfile {
  return {
    ...profile,
    id: slugifyProfileId(profile.id),
    name: profile.name.trim(),
    description: profile.description.trim(),
    domain_strategy: profile.domain_strategy.trim() || "IPIfNonMatch",
    rule_order: profile.rule_order.trim() || "block-proxy-direct",
    remote_dns_ip: profile.remote_dns_ip.trim() || "1.1.1.1",
    local_dns_ip: profile.local_dns_ip.trim() || "8.8.8.8",
    remote_dns_url: profile.remote_dns_url.trim() || "https://1.1.1.1/dns-query",
    direct_domains: sanitizeList(profile.direct_domains),
    direct_ips: sanitizeList(profile.direct_ips),
    proxy_domains: sanitizeList(profile.proxy_domains),
    proxy_ips: sanitizeList(profile.proxy_ips),
    block_domains: sanitizeList(profile.block_domains),
    block_ips: sanitizeList(profile.block_ips),
    source_url: profile.source_url?.trim() || null,
    update_interval_hours: Math.max(1, Math.floor(profile.update_interval_hours || 24)),
  };
}

function sanitizeList(items: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const item of items) {
    const trimmed = item.trim();
    if (!trimmed || seen.has(trimmed)) continue;
    seen.add(trimmed);
    out.push(trimmed);
  }
  return out;
}

function parseListInput(value: string): string[] {
  return sanitizeList(value.split(/\r?\n/));
}

function clampHours(value: string): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return 24;
  return Math.max(1, Math.min(24 * 365, Math.floor(numeric)));
}

function slugifyProfileId(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 64);
}

function makeUniqueProfileId(prefix: string, profiles: Array<{ id: string }>): string {
  const existing = new Set(profiles.map((profile) => profile.id));
  for (let index = 1; index < 100; index += 1) {
    const candidate = `${prefix}-${Date.now().toString(36)}${index === 1 ? "" : `-${index}`}`;
    if (!existing.has(candidate)) return candidate;
  }
  return `${prefix}-${crypto.randomUUID()}`;
}

function makeUniqueProfileName(name: string, profiles: Array<{ name: string }>): string {
  const existing = new Set(profiles.map((profile) => profile.name));
  if (!existing.has(name)) return name;
  for (let index = 2; index < 100; index += 1) {
    const candidate = `${name} ${index}`;
    if (!existing.has(candidate)) return candidate;
  }
  return `${name} ${Date.now().toString(36)}`;
}

function makeCopyProfileId(sourceId: string): string {
  const slug = slugifyProfileId(sourceId).slice(0, 42) || "profile";
  return `${slug}-copy-${Date.now().toString(36)}`;
}

function makeCopyProfileName(name: string, profiles: Array<{ name: string }>, suffix: string): string {
  const baseName = `${name}${suffix}`;
  return makeUniqueProfileName(baseName, profiles);
}

function RoutingActionButton({
  children,
  title,
  onClick,
  spinning = false,
}: {
  children: ReactNode;
  title: string;
  onClick?: () => void;
  spinning?: boolean;
}) {
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      disabled={spinning}
      className="routing-action-button"
    >
      {children}
    </button>
  );
}

function DuplicateIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="8" y="8" width="11" height="11" rx="2" />
      <path d="M5 15H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1" />
    </svg>
  );
}

function EditIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
    </svg>
  );
}

function FolderIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />
    </svg>
  );
}

function RefreshIcon({ spinning }: { spinning: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={["h-4 w-4", spinning ? "animate-spin" : ""].join(" ")}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 6v5h-5" />
      <path d="M4 18v-5h5" />
      <path d="M19 11a7 7 0 0 0-12-4l-3 3" />
      <path d="M5 13a7 7 0 0 0 12 4l3-3" />
    </svg>
  );
}

function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

function ClipboardIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M9 4h6" />
      <path d="M9 4a3 3 0 0 0 6 0" />
      <rect x="6" y="5" width="12" height="16" rx="2" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M19 6l-1 14H6L5 6" />
      <path d="M10 11v5M14 11v5" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M18 6 6 18M6 6l12 12" />
    </svg>
  );
}
