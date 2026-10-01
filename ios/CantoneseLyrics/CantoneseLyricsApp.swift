import SwiftUI

@main
struct CantoneseLyricsApp: App {
    @StateObject private var store = LyricsStore()
    @StateObject private var settings = AppSettings()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .environmentObject(settings)
        }
    }
}
