import AppKit
import Foundation
import WebKit

final class NavigationDelegate: NSObject, WKNavigationDelegate {
  var finished = false
  var navigationError: Error?

  func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
    finished = true
  }

  func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
    navigationError = error
    finished = true
  }

  func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
    navigationError = error
    finished = true
  }
}

enum OgRenderError: Error {
  case invalidArguments
  case couldNotReadSvg
  case loadFailed(String)
  case snapshotFailed(String)
  case missingImage
  case pngEncodingFailed
}

func waitUntil(_ condition: @autoclosure @escaping () -> Bool, timeout: TimeInterval) -> Bool {
  let deadline = Date().addingTimeInterval(timeout)
  while !condition() && Date() < deadline {
    RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.01))
  }
  return condition()
}

func renderOgImage(inputPath: String, outputPath: String) throws {
  let inputUrl = URL(fileURLWithPath: inputPath)
  let outputUrl = URL(fileURLWithPath: outputPath)

  let svgMarkup = try String(contentsOf: inputUrl, encoding: .utf8)
  let canvasWidth = 1200
  let canvasHeight = 630
  let html = """
  <!doctype html>
  <html>
    <head>
      <meta charset="utf-8" />
      <style>
        html, body {
          margin: 0;
          padding: 0;
          width: \(canvasWidth)px;
          height: \(canvasHeight)px;
          overflow: hidden;
          background: transparent;
        }

        body > svg {
          display: block;
          width: \(canvasWidth)px;
          height: \(canvasHeight)px;
        }
      </style>
    </head>
    <body>
      \(svgMarkup)
    </body>
  </html>
  """

  let webView = WKWebView(
    frame: NSRect(x: 0, y: 0, width: canvasWidth, height: canvasHeight),
    configuration: WKWebViewConfiguration(),
  )
  webView.setValue(false, forKey: "drawsBackground")
  let delegate = NavigationDelegate()
  webView.navigationDelegate = delegate
  webView.loadHTMLString(html, baseURL: inputUrl.deletingLastPathComponent())

  guard waitUntil(delegate.finished, timeout: 10) else {
    throw OgRenderError.loadFailed("Timed out while loading SVG into WebKit.")
  }

  if let navigationError = delegate.navigationError {
    throw OgRenderError.loadFailed(navigationError.localizedDescription)
  }

  let snapshotConfig = WKSnapshotConfiguration()
  snapshotConfig.rect = CGRect(x: 0, y: 0, width: canvasWidth, height: canvasHeight)
  snapshotConfig.snapshotWidth = NSNumber(value: canvasWidth)

  var capturedImage: NSImage?
  var snapshotError: Error?
  webView.takeSnapshot(with: snapshotConfig) { image, error in
    capturedImage = image
    snapshotError = error
    delegate.finished = true
  }
  delegate.finished = false

  guard waitUntil(delegate.finished, timeout: 10) else {
    throw OgRenderError.snapshotFailed("Timed out while capturing the Open Graph image.")
  }

  if let snapshotError {
    throw OgRenderError.snapshotFailed(snapshotError.localizedDescription)
  }

  guard let capturedImage else {
    throw OgRenderError.missingImage
  }

  guard
    let tiffData = capturedImage.tiffRepresentation,
    let bitmap = NSBitmapImageRep(data: tiffData),
    let pngData = bitmap.representation(using: .png, properties: [:])
  else {
    throw OgRenderError.pngEncodingFailed
  }

  try pngData.write(to: outputUrl)
}

let arguments = CommandLine.arguments
guard arguments.count == 3 else {
  fputs("Usage: swift generate-og-image.swift <input-svg> <output-png>\n", stderr)
  throw OgRenderError.invalidArguments
}

let application = NSApplication.shared
application.setActivationPolicy(.prohibited)

do {
  try renderOgImage(inputPath: arguments[1], outputPath: arguments[2])
} catch {
  fputs("Failed to generate OG image: \(error)\n", stderr)
  exit(1)
}
