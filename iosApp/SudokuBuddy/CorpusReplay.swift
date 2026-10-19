#if DEBUG
import Foundation

/// Local, opt-in simulator replay. Never sends photos or reports over the network.
@MainActor enum CorpusReplay {
    private static var started = false
    static func runIfRequested() async {
        guard ProcessInfo.processInfo.arguments.contains("--corpus-replay"), !started else { return }
        started = true
        let arguments=ProcessInfo.processInfo.arguments
        let selectedPhoto=arguments.firstIndex(of:"--corpus-photo").flatMap { $0+1<arguments.count ? arguments[$0+1] : nil }
        let root = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("CorpusReplay")
        await Task.detached(priority: .utility) {
            let manager = FileManager.default
            let output = root.appendingPathComponent("results.jsonl")
            manager.createFile(atPath: output.path, contents: nil)
            guard let handle = try? FileHandle(forWritingTo: output) else { return }
            defer { try? handle.close() }
            for folder in ["corpus", "corpus-rectified"] {
                let files = (try? manager.contentsOfDirectory(at: root.appendingPathComponent(folder), includingPropertiesForKeys: nil)) ?? []
                for file in files.sorted(by: { $0.lastPathComponent < $1.lastPathComponent }) where ["jpg", "jpeg", "png", "webp"].contains(file.pathExtension.lowercased()) {
                    if let selectedPhoto, file.lastPathComponent != selectedPhoto { continue }
                    let start = Date()
                    let row: [String: Any] = autoreleasepool {
                        do {
                            let scan = try PhotoScanner.read(Data(contentsOf: file))
                            return ["folder": folder, "photo": file.lastPathComponent, "status": "review", "locator": scan.locator, "givens": scan.givens, "progress": scan.progress,
                                    "uncertain": scan.uncertain.sorted(), "evidence": String(data:(try? JSONEncoder().encode(scan.evidence)) ?? Data(),encoding:.utf8) ?? "", "seconds": Date().timeIntervalSince(start)]
                        } catch {
                            return ["folder": folder, "photo": file.lastPathComponent, "status": "refused", "error": error.localizedDescription,
                                    "seconds": Date().timeIntervalSince(start)]
                        }
                    }
                    if let data = try? JSONSerialization.data(withJSONObject: row, options: [.sortedKeys]) {
                        try? handle.write(contentsOf: data + Data([10])); try? handle.synchronize()
                    }
                }
            }
            try? Data("complete".utf8).write(to: root.appendingPathComponent("complete"), options: .atomic)
        }.value
    }
}
#endif
