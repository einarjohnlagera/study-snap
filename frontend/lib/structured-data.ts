import { SITE_NAME, SITE_URL } from "@/lib/site-metadata";

type PublicNoteStructuredDataInput = {
  title: string;
  description: string;
  canonicalUrl: string;
  authorName?: string | null;
  updatedAt?: string | null;
  createdAt?: string | null;
  tags?: string[];
  subject?: string | null;
};

export function buildWebsiteStructuredData(description: string) {
  return {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: SITE_NAME,
    url: SITE_URL,
    description,
  };
}

export function buildCollectionPageStructuredData({
  name,
  url,
  description,
}: {
  name: string;
  url: string;
  description: string;
}) {
  return {
    "@context": "https://schema.org",
    "@type": "CollectionPage",
    name,
    url,
    description,
  };
}

export function buildBreadcrumbStructuredData(items: { name: string; url: string }[]) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((item, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: item.name,
      item: item.url,
    })),
  };
}

export function buildFaqPageStructuredData(faqs: { question: string; answer: string }[]) {
  return {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: faqs.map((faq) => ({
      "@type": "Question",
      name: faq.question,
      acceptedAnswer: {
        "@type": "Answer",
        text: faq.answer,
      },
    })),
  };
}

export function buildArticleStructuredData({
  title,
  description,
  canonicalUrl,
  authorName,
  updatedAt,
  createdAt,
  tags,
  subject,
}: PublicNoteStructuredDataInput) {
  return {
    "@context": "https://schema.org",
    "@type": "Article",
    headline: title,
    description,
    author: authorName
      ? {
          "@type": "Person",
          name: authorName,
        }
      : undefined,
    dateModified: updatedAt || undefined,
    datePublished: createdAt || undefined,
    mainEntityOfPage: {
      "@type": "WebPage",
      "@id": canonicalUrl,
    },
    keywords: tags && tags.length > 0 ? tags.join(", ") : undefined,
    articleSection: subject?.trim() ? subject.trim() : undefined,
    publisher: {
      "@type": "Organization",
      name: SITE_NAME,
      url: SITE_URL,
    },
  };
}
