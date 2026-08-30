import type { ButtonHTMLAttributes, CSSProperties, PropsWithChildren } from "react";

export function Button({
  variant,
  className,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "danger" }) {
  const cls = ["cm-btn", variant === "primary" ? "cm-btn-primary" : "", variant === "danger" ? "cm-btn-danger" : "", className]
    .filter(Boolean)
    .join(" ");
  return <button className={cls} {...rest} />;
}

export function Card({ children, className, style }: PropsWithChildren<{ className?: string; style?: CSSProperties }>) {
  return (
    <div className={["cm-card", className].filter(Boolean).join(" ")} style={style}>
      {children}
    </div>
  );
}

export function PageHeader({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="cm-page-header">
      <div>
        <h1>{title}</h1>
        {subtitle ? <p>{subtitle}</p> : null}
      </div>
    </div>
  );
}

export function Badge({ status }: { status: string }) {
  return <span className={`cm-badge cm-badge-${status.toLowerCase()}`}>{status.replace(/_/g, " ")}</span>;
}

export function Spinner({ label = "Loading…" }: { label?: string }) {
  return <div className="cm-spinner">{label}</div>;
}

export function ErrorBanner({ message }: { message: string }) {
  return <div className="cm-error-banner">{message}</div>;
}

export function Field({ label, children }: PropsWithChildren<{ label: string }>) {
  return (
    <div className="cm-field">
      <label>{label}</label>
      {children}
    </div>
  );
}

export function friendlyError(err: unknown): string {
  if (err && typeof err === "object" && "problem" in err) {
    const p = (err as { problem?: { title?: string } }).problem;
    if (p?.title) return p.title;
  }
  if (err instanceof Error) return err.message;
  return "Something went wrong";
}
