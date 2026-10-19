import SwiftUI
import UIKit
import AVFoundation
import CoreImage
import SudokuShared
import GridGeometry
import PuzzlePersistence

struct LiveCameraCapture: UIViewControllerRepresentable {
    let automatic: Bool
    let completion: (Data?) -> Void
    func makeUIViewController(context: Context) -> LiveCameraController { LiveCameraController(automatic: automatic, completion: completion) }
    func updateUIViewController(_ controller: LiveCameraController, context: Context) { controller.setAutomatic(automatic) }
}

final class LiveCameraController: UIViewController {
    private let engine: LiveCameraSession
    private let completion: (Data?) -> Void
    private let preview = AVCaptureVideoPreviewLayer()
    private let outline = CAShapeLayer()
    private let reticle = CAShapeLayer()
    private let guidance = UILabel()
    private let shutter = UIButton(type: .system)
    private let retry = UIButton(type: .system)
    private var finished = false
    private func finish(_ data: Data?) {
        guard !finished else { return }; finished = true; engine.stop(); completion(data)
    }
    init(automatic: Bool, completion: @escaping (Data?) -> Void) {
        engine = LiveCameraSession(automatic: automatic); self.completion = completion
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }
    func setAutomatic(_ enabled: Bool) { engine.setAutomatic(enabled) }
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = .black
        preview.session = engine.session; preview.videoGravity = .resizeAspectFill; view.layer.addSublayer(preview)
        outline.strokeColor = UIColor(red: 102/255, green: 187/255, blue: 106/255, alpha: 1).cgColor
        outline.fillColor = UIColor(red: 102/255, green: 187/255, blue: 106/255, alpha: 0.12).cgColor
        outline.lineWidth = 2; view.layer.addSublayer(outline)
        reticle.strokeColor = UIColor.white.withAlphaComponent(0.85).cgColor; reticle.fillColor = UIColor.clear.cgColor
        reticle.lineWidth = 4; reticle.lineCap = .round; view.layer.addSublayer(reticle)
        guidance.textColor = .white; guidance.backgroundColor = UIColor.black.withAlphaComponent(0.65)
        guidance.numberOfLines = 0; guidance.textAlignment = .center; guidance.font = .preferredFont(forTextStyle: .body)
        guidance.adjustsFontForContentSizeCategory = true; guidance.translatesAutoresizingMaskIntoConstraints = false
        guidance.text = tr("Point the camera at a sudoku puzzle"); view.addSubview(guidance)
        let close = UIButton(type: .system)
        close.setTitle(tr("Cancel"), for: .normal); close.addAction(UIAction { [weak self] _ in self?.finish(nil) }, for: .touchUpInside)
        shutter.layer.cornerRadius = 38; shutter.layer.borderColor = UIColor.white.cgColor; shutter.layer.borderWidth = 4
        let disc = UIView(); disc.backgroundColor = .white; disc.layer.cornerRadius = 29; disc.isUserInteractionEnabled = false
        disc.translatesAutoresizingMaskIntoConstraints = false; shutter.addSubview(disc)
        NSLayoutConstraint.activate([disc.centerXAnchor.constraint(equalTo: shutter.centerXAnchor), disc.centerYAnchor.constraint(equalTo: shutter.centerYAnchor), disc.widthAnchor.constraint(equalToConstant: 58), disc.heightAnchor.constraint(equalToConstant: 58)])
        shutter.addAction(UIAction { [weak self] _ in self?.engine.capture() }, for: .touchUpInside)
        shutter.accessibilityLabel = tr("Take photo")
        retry.setTitle(tr("Retry camera"), for: .normal); retry.isHidden = true
        retry.addAction(UIAction { [weak self] _ in
            self?.retry.isHidden = true; self?.shutter.isHidden = false; self?.engine.start()
        }, for: .touchUpInside)
        retry.tintColor = .white; retry.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(retry)
        for button in [close, shutter] { button.tintColor = .white; button.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(button) }
        NSLayoutConstraint.activate([
            retry.centerXAnchor.constraint(equalTo: shutter.centerXAnchor), retry.centerYAnchor.constraint(equalTo: shutter.centerYAnchor), retry.widthAnchor.constraint(greaterThanOrEqualToConstant: 160), retry.heightAnchor.constraint(equalToConstant: 60),
            close.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 20),
            close.centerYAnchor.constraint(equalTo: shutter.centerYAnchor), close.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
            shutter.centerXAnchor.constraint(equalTo: view.centerXAnchor), shutter.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20),
            shutter.heightAnchor.constraint(equalToConstant: 76), shutter.widthAnchor.constraint(equalToConstant: 76),
            guidance.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 20),
            guidance.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20),
            guidance.bottomAnchor.constraint(equalTo: shutter.topAnchor, constant: -16)
        ])
        engine.onFrame = { [weak self] points, message, accepted in
            DispatchQueue.main.async {
                guard let self, !self.finished else { return }
                self.guidance.text = tr(message)
                let unavailable = message == "The camera is unavailable. Close other camera apps, then try again."
                self.shutter.isHidden = unavailable; self.retry.isHidden = !unavailable
                self.guidance.textColor = unavailable ? UIColor(red: 239/255, green: 83/255, blue: 80/255, alpha: 1) : .white
                let path = UIBezierPath()
                if accepted, let first = points.first { path.move(to: first); points.dropFirst().forEach { path.addLine(to: $0) }; path.close() }
                self.outline.path = path.cgPath
            }
        }
        engine.onPhoto = { [weak self] data in DispatchQueue.main.async { self?.finish(data) } }
    }
    override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); engine.start() }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); engine.stop() }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews(); preview.frame = view.bounds; outline.frame = view.bounds; reticle.frame = view.bounds
        let top = view.safeAreaInsets.top + 80, bottom = guidance.frame.minY - 20
        let side = max(0, min(view.bounds.width - 40, bottom - top))
        let square = CGRect(x: (view.bounds.width-side)/2,y: top+(bottom-top-side)/2,width: side,height: side)
        let arm = side*0.14, path = UIBezierPath()
        for (x,y,sx,sy) in [(square.minX,square.minY,CGFloat(1),CGFloat(1)),(square.maxX,square.minY,-1,1),(square.minX,square.maxY,1,-1),(square.maxX,square.maxY,-1,-1)] {
            path.move(to: CGPoint(x:x+sx*arm,y:y));path.addLine(to: CGPoint(x:x,y:y));path.addLine(to: CGPoint(x:x,y:y+sy*arm))
        }
        reticle.path = path.cgPath
        let orientation: AVCaptureVideoOrientation
        switch view.window?.windowScene?.interfaceOrientation {
        case .landscapeLeft: orientation = .landscapeLeft
        case .landscapeRight: orientation = .landscapeRight
        case .portraitUpsideDown: orientation = .portraitUpsideDown
        default: orientation = .portrait
        }
        if preview.connection?.isVideoOrientationSupported == true { preview.connection?.videoOrientation = orientation }
        engine.layout(size: view.bounds.size, orientation: orientation)
    }
}

private final class LiveCameraSession: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, AVCapturePhotoCaptureDelegate, @unchecked Sendable {
    let session = AVCaptureSession()
    var onFrame: (([CGPoint], String, Bool) -> Void)?
    var onPhoto: ((Data?) -> Void)?
    private let queue = DispatchQueue(label: "SudokuBuddy.camera", qos: .userInitiated)
    private let photo = AVCapturePhotoOutput()
    private let video = AVCaptureVideoDataOutput()
    private let context = CIContext(options: [.cacheIntermediates: false])
    private var automatic: Bool
    private var device: AVCaptureDevice?
    private var orientation: AVCaptureVideoOrientation = .portrait
    private var active = false
    private var observers: [NSObjectProtocol] = []
    private var configured = false
    private var captured = false
    private var lastAnalysis: CFTimeInterval = 0
    private let advisor = IosCaptureAdvisor()
    private var focusCenter: CGPoint?
    private var focusSpan: CGFloat = 0
    private var focusMisses = 0
    private var focusPending = false
    private var focusStarted: CFTimeInterval = 0
    private var previewSize = CGSize(width: 1, height: 1)
    init(automatic: Bool) {
        self.automatic = automatic; super.init()
        let center = NotificationCenter.default
        for name in [NSNotification.Name.AVCaptureSessionWasInterrupted, .AVCaptureSessionRuntimeError] {
            observers.append(center.addObserver(forName: name, object: session, queue: nil) { [weak self] _ in
                guard let self else { return }
                self.queue.async { self.advisor.reset(); self.onFrame?([], "The camera is unavailable. Close other camera apps, then try again.", false) }
            })
        }
        observers.append(center.addObserver(forName: .AVCaptureSessionInterruptionEnded, object: session, queue: nil) { [weak self] _ in
            guard let self else { return }
            self.queue.async { if self.active { self.advisor.reset(); if !self.session.isRunning { self.session.startRunning() } } }
        })
    }
    deinit { observers.forEach(NotificationCenter.default.removeObserver) }
    func start() {
        queue.async { [self] in
            active = true
            if !configured {
                do {
                    guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else { throw ScanFailure.message("The camera is unavailable. Close other camera apps, then try again.") }
                    let input = try AVCaptureDeviceInput(device: device)
                    session.beginConfiguration(); session.sessionPreset = .photo
                    guard session.canAddInput(input), session.canAddOutput(photo), session.canAddOutput(video) else {
                        session.commitConfiguration(); throw ScanFailure.message("The camera is unavailable. Close other camera apps, then try again.")
                    }
                    session.addInput(input); session.addOutput(photo); session.addOutput(video)
                    video.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange]
                    video.alwaysDiscardsLateVideoFrames = true; video.setSampleBufferDelegate(self, queue: queue)
                    self.device = device; configured = true
                    session.commitConfiguration()
                    applyOrientation()
                } catch { onFrame?([], error.localizedDescription, false); return }
            }
            if !session.isRunning { session.startRunning() }
        }
    }
    func stop() { queue.async { [self] in active = false; if session.isRunning { session.stopRunning() } } }
    func layout(size: CGSize, orientation: AVCaptureVideoOrientation) {
        queue.async { [self] in
            self.orientation = orientation
            previewSize = size; advisor.reset()
            applyOrientation()
        }
    }
    private func applyOrientation() {
            for connection in [photo.connection(with: .video), video.connection(with: .video)] {
                if connection?.isVideoOrientationSupported == true { connection?.videoOrientation = orientation }
            }
    }
    func setAutomatic(_ enabled: Bool) { queue.async { [self] in automatic = enabled } }
    func capture() { queue.async { [self] in takePhoto() } }
    private func takePhoto() {
        guard active, configured, session.isRunning, !captured else { return }
        captured = true
        let settings = AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.jpeg])
        if photo.supportedFlashModes.contains(.off) { settings.flashMode = .off }
        photo.capturePhoto(with: settings, delegate: self)
    }
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        queue.async { [self] in
            guard active else { captured = false; return }
            if error != nil || photo.fileDataRepresentation() == nil {
                captured = false; advisor.reset(); onFrame?([], "The camera could not take that photo.", false)
            } else { onPhoto?(photo.fileDataRepresentation()) }
        }
    }
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = CACurrentMediaTime()
        guard active, !captured, now - lastAnalysis >= 0.2, let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastAnalysis = now
        autoreleasepool {
            let frame = CGSize(width: CVPixelBufferGetWidth(buffer), height: CVPixelBufferGetHeight(buffer))
            let scale = min(1, 720 / min(frame.width, frame.height))
            let width = Int(frame.width * scale), height = Int(frame.height * scale)
            let reduced = CIImage(cvPixelBuffer: buffer).transformed(by: CGAffineTransform(scaleX: scale, y: scale))
            var rgba = [UInt8](repeating: 0, count: width * height * 4)
            rgba.withUnsafeMutableBytes { bytes in context.render(reduced, toBitmap: bytes.baseAddress!, rowBytes: width * 4,
                bounds: CGRect(x: 0, y: 0, width: width, height: height), format: .RGBA8, colorSpace: CGColorSpace(name: CGColorSpace.sRGB)) }
            var rgb = Data(count: width * height * 3)
            rgb.withUnsafeMutableBytes { target in
                let dst = target.bindMemory(to: UInt8.self)
                for i in 0..<(width * height) { for c in 0..<3 { dst[i*3+c] = rgba[i*4+c] } }
            }
            guard let found = SudokuGridGeometry.framingRGB(rgb, width: width, height: height),
                  let gray = found["luminance"] as? Data, let grid = found["gridLuminance"] as? Data else { return }
            let corners = (found["corners"] as? [[NSNumber]] ?? []).compactMap { pair -> CGPoint? in
                guard pair.count == 2 else { return nil }; return CGPoint(x: pair[0].doubleValue, y: pair[1].doubleValue)
            }
            let accepted = (found["accepted"] as? NSNumber)?.boolValue == true
            if accepted, corners.count == 4 {
                let center = CGPoint(x: corners.map(\.x).reduce(0,+)/4, y: corners.map(\.y).reduce(0,+)/4)
                let span = min(corners.map(\.x).max()! - corners.map(\.x).min()!, corners.map(\.y).max()! - corners.map(\.y).min()!)
                focusMisses = 0
                if !focusPending && (focusCenter == nil || abs(center.x-focusCenter!.x)>0.08 || abs(center.y-focusCenter!.y)>0.08 || abs(span-focusSpan)>focusSpan*0.2) {
                    focusCenter = center; focusSpan = span
                    if let device, device.isFocusPointOfInterestSupported {
                        do {
                            try device.lockForConfiguration()
                            // Capture buffers are already upright; the lens metering coordinates are sensor-oriented.
                            let sensor: CGPoint
                            switch orientation {
                            case .portrait: sensor = CGPoint(x: center.y, y: 1-center.x)
                            case .portraitUpsideDown: sensor = CGPoint(x: 1-center.y, y: center.x)
                            case .landscapeLeft: sensor = CGPoint(x: 1-center.x, y: 1-center.y)
                            default: sensor = center
                            }
                            device.focusPointOfInterest = sensor
                            if device.isFocusModeSupported(.autoFocus) { device.focusMode = .autoFocus; focusPending = true; focusStarted = now }
                            if device.isExposurePointOfInterestSupported { device.exposurePointOfInterest = sensor }
                            if device.isExposureModeSupported(.continuousAutoExposure) { device.exposureMode = .continuousAutoExposure }
                            device.unlockForConfiguration(); advisor.reset()
                        } catch { /* Fixed-focus or temporarily unavailable: keep the manual shutter and quality gate. */ }
                    }
                }
            } else { focusMisses += 1; if focusMisses > 2 { focusCenter = nil } }
            if focusPending {
                if (device?.isAdjustingFocus == true || now - focusStarted < 0.3) && now - focusStarted < 5 { advisor.reset() }
                else { focusPending = false; advisor.reset() }
            }
            let json = advisor.observe(width: Int32(width), height: Int32(height), luminance: gray,
                corners: corners.flatMap { [String(Double($0.x)), String(Double($0.y))] }.joined(separator: ","),
                gridSide: (found["gridSide"] as? NSNumber)?.int32Value ?? 0, gridLuminance: grid,
                candidates: (found["candidates"] as? NSNumber)?.int32Value ?? 0, nearGrid: (found["nearGrid"] as? NSNumber)?.boolValue ?? false)
            guard let data = json.data(using: .utf8), let advice = try? JSONDecoder().decode(CameraAdvice.self, from: data) else { return }
            onFrame?(corners.map { CaptureGate.onScreen($0, frame: frame, preview: previewSize) }, advice.message, advice.accepted)
            if automatic && advice.ready && !focusPending { takePhoto() }

        }
    }
}

private struct CameraAdvice: Decodable { let message: String; let ready: Bool; let accepted: Bool }
