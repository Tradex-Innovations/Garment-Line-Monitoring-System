const rawBasePath = import.meta.env.BASE_URL || "/";

export const routerBasename =
  rawBasePath === "/" ? "/" : rawBasePath.replace(/\/+$/, "");

export function normalizeRouterPathname(pathname: string) {
  if (!pathname) return "/";
  if (routerBasename === "/") return pathname;
  if (pathname === routerBasename) return "/";
  if (pathname.startsWith(`${routerBasename}/`)) {
    return pathname.slice(routerBasename.length) || "/";
  }
  return pathname;
}
