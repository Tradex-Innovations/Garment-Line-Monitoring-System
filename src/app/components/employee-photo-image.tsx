import { useState, type CSSProperties, type ReactNode } from "react";
import { useEmployeePhotoUrl } from "@/lib/employee-photos";

export function EmployeePhotoImage({
  reference,
  alt,
  className,
  style,
  placeholder,
}: {
  reference?: string | null;
  alt: string;
  className: string;
  style?: CSSProperties;
  placeholder: ReactNode;
}) {
  const url = useEmployeePhotoUrl(reference);
  const [failedUrl, setFailedUrl] = useState("");
  return url && url !== failedUrl ? (
    <img src={url} alt={alt} className={className} style={style} referrerPolicy="no-referrer"
      onError={() => setFailedUrl(url)} />
  ) : (
    <>{placeholder}</>
  );
}
