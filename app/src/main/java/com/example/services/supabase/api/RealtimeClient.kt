package com.example.services.supabase.api

import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class RealtimeClient(
    private val client: OkHttpClient,
    private val supabaseUrl: String,
    private val anonKey: String,
    private var sessionId: String? = null
) {
    private var webSocket: WebSocket? = null
    private val _updates = MutableSharedFlow<JSONObject>(extraBufferCapacity = 100)
    val updates: SharedFlow<JSONObject> = _updates.asSharedFlow()
    
    private var ref = 1

    fun updateSessionId(newSessionId: String) {
        this.sessionId = newSessionId
    }
    
    fun connect() {
        val wssUrl = supabaseUrl.replace("https://", "wss://").replace("http://", "ws://") + 
                     "/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
        
        val request = Request.Builder().url(wssUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("Realtime", "Connected")
                val joinMsg = JSONObject().apply {
                    put("topic", "realtime:public")
                    put("event", "phx_join")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("postgres_changes", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("table", "live_broadcast_sessions")
                                })
                                put(JSONObject().apply {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("table", "live_broadcast_teams")
                                })
                                put(JSONObject().apply {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("table", "live_broadcast_players")
                                })
                                put(JSONObject().apply {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("table", "live_broadcast_events")
                                })
                            })
                        })
                    })
                    put("ref", "${ref++}")
                }
                webSocket.send(joinMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val event = json.optString("event")
                    val payload = json.optJSONObject("payload")
                    val payloadType = payload?.optString("type") ?: payload?.optString("event")
                    if (event == "postgres_changes" || payloadType == "postgres_changes" || payload?.has("data") == true || payload?.has("record") == true) {
                        _updates.tryEmit(json)
                    }
                } catch (e: Exception) {
                    Log.e("Realtime", "Parse error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("Realtime", "Error", t)
            }
        })
    }
    
    fun heartbeat() {
        val msg = JSONObject().apply {
            put("topic", "phoenix")
            put("event", "heartbeat")
            put("payload", JSONObject())
            put("ref", "${ref++}")
        }
        webSocket?.send(msg.toString())
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
    }
}
