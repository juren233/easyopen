import SwiftUI
import UIKit
import EasyOpenShared

private struct ComposeRootViewController: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosMainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct EasyOpenIOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRootViewController()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(uiColor: .systemBackground))
                .ignoresSafeArea()
        }
    }
}
