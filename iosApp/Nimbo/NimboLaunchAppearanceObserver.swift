import SwiftUI
import UIKit

/// Appearance + two subsequent display callbacks, delivered after the callback
/// returns. This observes native lifecycle/display opportunities, NOT GPU pixels.
@MainActor
struct NimboLaunchAppearanceObserver: UIViewControllerRepresentable {
    let acknowledged: () -> Void

    func makeUIViewController(context: Context) -> Observer {
        Observer(acknowledged: acknowledged)
    }
    func updateUIViewController(_ controller: Observer, context: Context) {}

    static func dismantleUIViewController(_ controller: Observer, coordinator: ()) {
        controller.stopObserving()
    }

    final class Observer: UIViewController {
        private let acknowledged: () -> Void
        private var displayLink: CADisplayLink?
        private var ticks = 0
        private var delivered = false
        private var visible = false
        private lazy var target = DisplayTarget(owner: self)

        init(acknowledged: @escaping () -> Void) {
            self.acknowledged = acknowledged
            super.init(nibName: nil, bundle: nil)
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }

        override func loadView() {
            view = UIView()
            view.backgroundColor = .clear
            view.isUserInteractionEnabled = false
            view.isAccessibilityElement = false
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            visible = true
            guard !delivered, displayLink == nil else { return }
            ticks = 0
            let link = CADisplayLink(target: target, selector: #selector(DisplayTarget.tick))
            displayLink = link
            link.add(to: .main, forMode: .common)
        }

        override func viewDidDisappear(_ animated: Bool) {
            super.viewDidDisappear(animated)
            stopObserving()
        }

        fileprivate func stopObserving() {
            visible = false
            displayLink?.invalidate()
            displayLink = nil
        }

        fileprivate func tick() {
            guard visible, view.window != nil,
                  view.window?.windowScene?.activationState == .foregroundActive else { return }
            ticks += 1
            guard ticks >= 2 else { return }
            displayLink?.invalidate()
            displayLink = nil
            DispatchQueue.main.async { [weak self] in
                guard let self, self.visible, self.view.window != nil, !self.delivered else { return }
                self.delivered = true
                self.acknowledged()
            }
        }
    }

    @MainActor private final class DisplayTarget: NSObject {
        weak var owner: Observer?
        init(owner: Observer) { self.owner = owner }
        @objc func tick() { owner?.tick() }
    }
}
