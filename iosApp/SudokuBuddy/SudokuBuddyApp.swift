import SwiftUI

@main
struct SudokuBuddyApp: App {
    @StateObject private var language = AppLanguage.shared
    var body: some Scene {
        WindowGroup {
            PuzzleView()
                .font(AndroidText.body)
#if DEBUG
                .task { await CorpusReplay.runIfRequested() }
                .modifier(DebugTextSize())
#endif
                .environment(\.locale, language.locale)
                .environment(\.layoutDirection, language.isRTL ? .rightToLeft : .leftToRight)
                .preferredColorScheme(.dark)
                .tint(AppStyle.primary)
        }
    }
}

#if DEBUG
private struct DebugTextSize: ViewModifier {
    @ViewBuilder func body(content: Content) -> some View {
        if ProcessInfo.processInfo.arguments.contains("--large-text-fixture") { content.dynamicTypeSize(.accessibility5) }
        else { content }
    }
}
#endif
