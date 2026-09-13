sed -i '/val broadcastCompositor/a \
    init {\
        (broadcastCompositor as? com.example.services.composition.BroadcastVideoCompositor)?.setTeamsProvider(supabaseTeamsProvider)\
    }\
' app/src/main/java/com/example/ui/LiveAnalyzerViewModel.kt
