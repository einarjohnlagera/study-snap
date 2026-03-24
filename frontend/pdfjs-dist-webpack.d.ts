declare module "pdfjs-dist/webpack.mjs" {
  type TextContentItem = {
    str?: string;
  };

  type PdfPage = {
    getTextContent(): Promise<{
      items: TextContentItem[];
    }>;
  };

  type PdfDocument = {
    numPages: number;
    getPage(pageNumber: number): Promise<PdfPage>;
  };

  export function getDocument(options: {
    data: Uint8Array;
  }): {
    promise: Promise<PdfDocument>;
  };
}
