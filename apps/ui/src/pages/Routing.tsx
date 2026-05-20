import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { api, type RoutingProfile, type RoutingProfileSummary } from "../lib/api";
import { useMessages, type Messages } from "../lib/i18n";
import { notifyError, notifyInfo } from "../lib/notify";

export function Routing() {
  const m = useMessages();
  const [profiles, setProfiles] = useState<RoutingProfileSummary[]>([]);
  const [activeId, setActiveId] = useState<string>("global");
  const [refreshing, setRefreshing] = useState(false);

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
          <button className="routing-add-button" title={m.routing.addCustom}>
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
          />
        ))}
      </div>
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
}: {
  profile: RoutingProfileSummary;
  active: boolean;
  labels: Messages;
  onActivate: () => void;
  onCopy: () => void;
  onDuplicate: () => void;
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
        <div className="routing-card-description">{profile.description}</div>
        <div className="routing-card-meta">
          <span className="routing-card-meta-pill">
            {profile.rules_count} rules
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
          aria-label={labels.routing.profileSettings}
          title={labels.routing.profileSettings}
          onClick={(e) => e.stopPropagation()}
        >
          <SettingsIcon />
        </button>
      </div>
    </div>
  );
}

function makeCopyProfileId(sourceId: string): string {
  const slug = sourceId
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 42) || "profile";
  return `${slug}-copy-${Date.now().toString(36)}`;
}

function makeCopyProfileName(name: string, profiles: Array<{ name: string }>, suffix: string): string {
  const baseName = `${name}${suffix}`;
  const existing = new Set(profiles.map((profile) => profile.name));
  if (!existing.has(baseName)) return baseName;
  for (let index = 2; index < 100; index += 1) {
    const candidate = `${baseName} ${index}`;
    if (!existing.has(candidate)) return candidate;
  }
  return `${baseName} ${Date.now().toString(36)}`;
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

function SettingsIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82L4.21 5.4a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z" />
    </svg>
  );
}
