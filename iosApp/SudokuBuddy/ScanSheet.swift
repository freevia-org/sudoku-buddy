import SwiftUI
import AVFoundation
import PhotosUI
import UniformTypeIdentifiers

struct ScanSheet: View {
    @ObservedObject private var language = AppLanguage.shared
    @Environment(\.dismiss) private var dismiss
    @State private var photo: PhotosPickerItem?
    @State private var showCamera = false
    @State private var cameraMenuOpen = false
    @State private var complaint: String?
    @State private var loading = false
    @State private var importTask: Task<Void, Never>?
    var canCancel = true
    let onNavigate: (String) -> Void
    @ObservedObject private var settings = AppSettings.shared
    let onPhoto: (Data) -> Void

    var body: some View {
        let choosePhoto = tr("Choose a photo")
        NavigationStack {
            VStack(spacing: 16) {
                ZStack {
                    Color.black
                    RoundedRectangle(cornerRadius: 4).stroke(AppStyle.secondary.opacity(0.6), style: StrokeStyle(lineWidth: 2, dash: [20, 80]))
                        .aspectRatio(1, contentMode: .fit).padding(32)
                    Text(tr("Point the camera at a sudoku puzzle"))
                        .multilineTextAlignment(.center).padding(40).foregroundStyle(.white)
                }.frame(maxWidth: .infinity, maxHeight: .infinity)
                Text(tr("Use a flat page in good light. Keep all four corners visible and fill the photo with the grid. Printed clues and handwritten answers will be read separately."))
                    .font(AndroidText.bodyMedium).padding(.horizontal, 20)
                if let complaint {
                    Text(tr(complaint)).font(AndroidText.bodyMedium).foregroundStyle(AppStyle.uncertain).padding(.horizontal, 20)
                    if AVCaptureDevice.authorizationStatus(for: .video) == .denied {
                        Button(tr("Open Settings")) {
                            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                        }
                    }
                }
                HStack {
                    PhotosPicker(selection: $photo, matching: .images, photoLibrary: .shared()) {
                        Image(systemName: "photo").font(AndroidText.titleLarge).frame(width: 56, height: 56)
                    }.accessibilityLabel(choosePhoto).disabled(loading)
                    Spacer()
                    Button { Task { await openCamera() } } label: {
                        Image(systemName: "circle.inset.filled").font(.system(size: 64)).foregroundStyle(AppStyle.primary)
                    }.accessibilityLabel(tr("Take photo")).disabled(loading)
                    Spacer()
                    Group { if loading { ProgressView() } else { Color.clear } }.frame(width: 56, height: 56)
                }.padding(.horizontal, 24).padding(.bottom, 12)
            }
            .background(AppStyle.background)
            .navigationBarTitleDisplayMode(.inline).navigationTitle(tr("Sudoku Buddy"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { if canCancel && !showCamera { Button(tr("Cancel")) { dismiss() } } }
            }
            .safeAreaInset(edge: .top, spacing: 0) { if !showCamera { cameraToolbar } }
            .interactiveDismissDisabled(!canCancel)
            .task {
                #if DEBUG
                if ProcessInfo.processInfo.arguments.contains("--camera-preview-fixture") { showCamera = true; return }
                #endif
                if UIImagePickerController.isSourceTypeAvailable(.camera) { await openCamera() }
                else { complaint = "A camera is not available here. Choose a photo instead." }
            }
            .onChange(of: photo) { item in
                guard let item else { return }
                loading = true
                importTask?.cancel()
                importTask = Task {
                    do {
                        guard let data = try await item.loadTransferable(type: Data.self) else { throw ScanFailure.message("That photo could not be opened.") }
                        guard !Task.isCancelled else { return }
                        onPhoto(data); dismiss()
                    } catch { complaint = error.localizedDescription }
                    loading = false
                    photo = nil
                }
            }
            .onDisappear { importTask?.cancel() }
            .accessibilityHidden(showCamera)
            .fullScreenCover(isPresented: $showCamera) {
                LiveCameraCapture(automatic: settings.autoCapture && !cameraMenuOpen) { data in
                    showCamera = false
                    if let data { onPhoto(data); dismiss() }
                }
                .ignoresSafeArea()
                .overlay(alignment: .top) { cameraToolbar }
            }
        }
    }

    private var cameraToolbar: some View {
        HStack(spacing: 12) {
            Button { navigate("history") } label: { Image(systemName: "line.3.horizontal").frame(width: 48, height: 48) }
                .accessibilityLabel(tr("Your puzzles")).accessibilityIdentifier("cameraHistory")
            Spacer(minLength: 0)
            Button { settings.autoCapture.toggle() } label: {
                HStack(spacing: 8) {
                    Text(tr("Auto photo")).lineLimit(1)
                    Toggle("", isOn: .constant(settings.autoCapture)).labelsHidden().allowsHitTesting(false).accessibilityHidden(true)
                }.font(AndroidText.labelLarge).padding(.horizontal, 14).frame(maxWidth: 220, minHeight: 48)
                    .background(.black.opacity(0.6), in: Capsule()).contentShape(Capsule())
            }.buttonStyle(.plain)
                .accessibilityRepresentation { Toggle(tr("Auto photo"), isOn: $settings.autoCapture).accessibilityIdentifier("cameraAutoCapture") }
            Spacer(minLength: 0)
            Button { cameraMenuOpen = true } label: { Image(systemName: "ellipsis").rotationEffect(.degrees(90)).frame(width: 48, height: 48) }
                .accessibilityLabel(tr("More")).accessibilityIdentifier("cameraMore")
                .confirmationDialog(tr("More"), isPresented: $cameraMenuOpen, titleVisibility: .hidden) {
                Button(tr("Strategies")) { navigate("strategies") }.accessibilityIdentifier("cameraStrategies")
                Button(tr("Settings")) { navigate("settings") }.accessibilityIdentifier("cameraSettings")
                Button(tr("About")) { navigate("about") }.accessibilityIdentifier("cameraAbout")
                Divider()
                Button(tr("Report a bug or idea on GitHub")) { navigate("bug") }.accessibilityIdentifier("cameraBug")
                Button(tr("Send diagnostics")) { navigate("diagnostics") }
                    Button(tr("Cancel"), role: .cancel) {}
                }

        }.foregroundStyle(.white).tint(AppStyle.primary).padding(8)
    }
    private func navigate(_ destination: String) { showCamera = false; onNavigate(destination); dismiss() }

    private func openCamera() async {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            complaint = "A camera is not available here. Choose a photo instead."
            return
        }
        loading = true
        defer { loading = false }
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        var allowed = status == .authorized
        if status == .notDetermined { allowed = await AVCaptureDevice.requestAccess(for: .video) }
        // Explicitly request only video access: no microphone or photo-library permission.
        if allowed { showCamera = true }
        else { complaint = "Camera access is unavailable. You can enable it in Settings or choose a photo." }
    }

}
