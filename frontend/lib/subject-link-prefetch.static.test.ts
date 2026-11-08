import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * A `<Link>` to a subject listing page MUST set `prefetch={false}`: Next.js 16.1.6 runs a route's `generateMetadata` when a link
 * to it is prefetched, and the subject listing's metadata walks the whole subject catalog (v0.179.0 B4 audit, real browser).
 * See the rule above `buildPublicLibrarySubjectPath` in lib/public-note-path.ts.
 *
 * This is a SAFEGUARD for the known construction patterns, not proof that every possible link is covered: a `router.prefetch()`
 * call, a plain object href, or an href assembled by another helper would escape it. Re-verify on every Next.js upgrade.
 */

type LinkTag = { tag: string; line: number };

/** Every `<Link ...>` opening tag in a source string, found by scanning braces so `=>` and `>` inside `{...}` do not end it. */
export function findLinkTags(source: string): LinkTag[] {
  const tags: LinkTag[] = [];
  const opener = /<Link\b/g;
  let match: RegExpExecArray | null;
  while ((match = opener.exec(source)) !== null) {
    let depth = 0;
    let index = match.index + match[0].length;
    let quote: string | null = null;
    for (; index < source.length; index += 1) {
      const char = source[index];
      if (quote) { if (char === quote) quote = null; continue; }
      if (char === '"' || char === "'" || char === "`") { quote = char; continue; }
      if (char === "{") depth += 1;
      else if (char === "}") depth -= 1;
      else if (char === ">" && depth === 0) break;
    }
    tags.push({ tag: source.slice(match.index, index + 1), line: source.slice(0, match.index).split("\n").length });
  }
  return tags;
}

/** The `<Link>` tags in a file that point at a subject listing but do not disable prefetch. */
export function subjectLinksWithoutPrefetchOff(source: string): LinkTag[] {
  const aliases = [...source.matchAll(/\bconst\s+([A-Za-z_$][\w$]*)\s*=\s*buildPublicLibrarySubjectPath\(/g)].map((m) => m[1]);
  return findLinkTags(source).filter(({ tag }) => {
    const href = /href=\{([\s\S]*?)\}\s*(?=[a-zA-Z-]+=|\/?>|\n)/.exec(tag)?.[1] ?? "";
    const pointsAtSubjectListing = /buildPublicLibrarySubjectPath\(/.test(tag)
      || aliases.some((alias) => new RegExp(`(^|[^\\w$.])${alias.replace("$", "\\$")}([^\\w$]|$)`).test(href))
      || /\/public\/library\/\$\{[^}]+\}(`|\?)/.test(tag)
      || /href="\/public\/library\/[a-z0-9-]+"/.test(tag);
    return pointsAtSubjectListing && !/prefetch=\{false\}/.test(tag);
  });
}

const root = process.cwd();
const sourceFiles = (["app", "components", "lib"] as const)
  .flatMap((dir) => readdirSync(join(root, dir), { recursive: true, encoding: "utf8" }).map((file) => join(dir, file)))
  .filter((file) => /\.tsx$/.test(file) && !/\.test\.tsx$/.test(file) && !file.includes("preview-verify"));

describe("subject listing links disable prefetch", () => {
  it("finds no <Link> to a subject listing without prefetch={false}", () => {
    const offenders = sourceFiles.flatMap((file) =>
      subjectLinksWithoutPrefetchOff(readFileSync(join(root, file), "utf8")).map(({ line }) => `${file}:${line}`));
    expect(offenders).toEqual([]);
  });

  it("still sees the three known subject links (so the scan itself is not silently empty)", () => {
    const known = [
      "app/public/library/[subject]/[slug]/page.tsx",
      "app/exam/[slug]/page.tsx",
    ];
    const withPrefetchOff = known.flatMap((file) => {
      const source = readFileSync(join(root, file), "utf8");
      return findLinkTags(source).filter(({ tag }) => /prefetch=\{false\}/.test(tag) && (/buildPublicLibrarySubjectPath\(/.test(tag) || /href=\{subjectPath\}/.test(tag))).map(() => file);
    });
    expect(withPrefetchOff.filter((file) => file.includes("library/[subject]/[slug]"))).toHaveLength(2); // breadcrumb + "See all"
    expect(withPrefetchOff.filter((file) => file.includes("exam/[slug]"))).toHaveLength(1); // subject chips
  });

  describe("detector (proves the scan can fail)", () => {
    it("flags a builder call without prefetch={false}", () => {
      const src = '<Link href={buildPublicLibrarySubjectPath(note.subject)} className="x">a</Link>';
      expect(subjectLinksWithoutPrefetchOff(src)).toHaveLength(1);
    });
    it("flags an alias variable without prefetch={false}", () => {
      const src = 'const subjectPath = buildPublicLibrarySubjectPath(label);\n<li><Link href={subjectPath} className="x">a</Link></li>';
      expect(subjectLinksWithoutPrefetchOff(src)).toHaveLength(1);
    });
    it("flags a subject-listing URL template and a literal subject path", () => {
      expect(subjectLinksWithoutPrefetchOff("<Link href={`/public/library/${slug}`}>a</Link>")).toHaveLength(1);
      expect(subjectLinksWithoutPrefetchOff('<Link href="/public/library/biology">a</Link>')).toHaveLength(1);
    });
    it("handles an arrow function and `>` inside braces in the same tag", () => {
      const src = '<Link href={buildPublicLibrarySubjectPath(x)} onClick={() => a > b} className="x">a</Link>';
      expect(subjectLinksWithoutPrefetchOff(src)).toHaveLength(1);
    });
    it("accepts the same links with prefetch={false}", () => {
      expect(subjectLinksWithoutPrefetchOff('<Link href={buildPublicLibrarySubjectPath(x)} prefetch={false} className="x">a</Link>')).toHaveLength(0);
      expect(subjectLinksWithoutPrefetchOff('const p = buildPublicLibrarySubjectPath(l);\n<Link href={p} prefetch={false}>a</Link>')).toHaveLength(0);
    });
    it("ignores note paths, the library index, and unrelated links", () => {
      expect(subjectLinksWithoutPrefetchOff("<Link href={`/public/library/${subject}/${slug}`}>a</Link>")).toHaveLength(0);
      expect(subjectLinksWithoutPrefetchOff('<Link href="/public/library">a</Link>')).toHaveLength(0);
      expect(subjectLinksWithoutPrefetchOff('<Link href={buildPublicLibraryUrl({ subject: s })}>a</Link>')).toHaveLength(0);
      expect(subjectLinksWithoutPrefetchOff('<Link href="/exam/ale">a</Link>')).toHaveLength(0);
    });
  });
});
