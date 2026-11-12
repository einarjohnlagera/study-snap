/** @jest-environment node */
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const roots = ["app", "components", "lib", "src"];
const extensions = new Set([".ts", ".tsx", ".js", ".jsx"]);

function filesUnder(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? filesUnder(path) : [path];
  });
}

test("non-test frontend code has no peso literal or static price-table read", () => {
  const offenders = roots.flatMap((root) => filesUnder(join(process.cwd(), root)))
    .filter((path) => extensions.has(path.slice(path.lastIndexOf("."))) && !/\.(test|spec)\.[jt]sx?$/.test(path))
    .filter((path) => {
      const content = readFileSync(path, "utf8");
      return /₱\d/.test(content) || content.includes("pricingConfig." + "price") || content.includes("pricingConfig." + "intro");
    });
  expect(offenders).toEqual([]);
});
