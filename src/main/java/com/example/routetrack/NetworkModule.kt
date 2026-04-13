package com.example.routetrack

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ===================== RESPONSE MODELS =====================

data class UserResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val user_id: Int? = null,
    val role: String? = null,
    val username: String? = null,
    val success: Boolean = false,
    val message: String? = null,
    val user: AuthUserData? = null
)

data class AuthUserData(
    val user_id: Int = 0,
    val username: String = "",
    val email: String = "",
    val role: String = "",
    val phone_number: String? = null
)

data class BusDetail(
    val bus_id: Int = 0,
    val bus_number: String = "",
    val route: String = "",
    val routeName: String? = null,
    val routeDescription: String? = null,
    val driver_name: String = "",
    // ── NEW: driver phone number displayed to students ──
    val driver_phone: String? = null,
    val next_stop: String? = null,
    val next_stop_time: String? = null,
    val scheduledTime: String? = null,
    val expectedTime: String? = null,
    val current_latitude: Double = 0.0,
    val current_longitude: Double = 0.0,
    val delay_minutes: Int = 0,
    val status: String = "Unknown",
    val statusColor: String? = null,
    val is_trip_active: Boolean = false
) {
    val displayRouteName: String get() = routeName ?: route.ifEmpty { "Unknown Route" }
    val displayDescription: String get() = routeDescription ?: "Driver: $driver_name"
    val displayScheduled: String get() = scheduledTime ?: "N/A"
    val displayExpected: String get() = expectedTime ?: "N/A"
    /** Formatted driver phone, empty string if not available */
    val displayDriverPhone: String get() = driver_phone?.takeIf { it.isNotBlank() } ?: ""
}

data class BusesResponse(
    val success: Boolean = false,
    val buses: List<BusDetail> = emptyList()
)

data class HealthResponse(
    val status: String? = null,
    val version: String? = null,
    val timestamp: String? = null
)

// ── NEW: response from GET /api/auth/check-driver-phone ─────────────────
data class DriverPhoneCheckResponse(
    val registered: Boolean = false,
    val already_has_account: Boolean = false,
    val driver_name: String? = null
)

// ===================== REQUEST MODELS =====================

data class LoginRequest(
    val username: String,
    val password: String,
    val role: String
)

data class SignupRequest(
    val username: String,
    val email: String,
    val password: String,
    val role: String,
    val phone_number: String? = null
)

data class LocationUpdateRequest(
    val bus_number: String,
    val latitude: Double,
    val longitude: Double,
    val is_trip_active: Boolean
)

data class LocationUpdateResponse(
    val success: Boolean = false,
    val message: String? = null
)

// ===================== API SERVICE INTERFACE =====================

interface ApiService {

    @POST("api/auth/login")
    suspend fun loginJson(@Body request: LoginRequest): UserResponse

    @POST("api/auth/signup")
    suspend fun signup(@Body request: SignupRequest): UserResponse

    /**
     * Checks whether a phone number is pre-registered by the admin.
     * Used on the driver signup screen to give instant feedback.
     * Query param: phone (raw string, server strips non-digits)
     */
    @GET("api/auth/check-driver-phone")
    suspend fun checkDriverPhone(@Query("phone") phone: String): DriverPhoneCheckResponse

    @GET("api/buses/search")
    suspend fun getBuses(): BusesResponse

    @POST("api/driver/location")
    suspend fun updateDriverLocation(@Body request: LocationUpdateRequest): LocationUpdateResponse

    @GET("api/health")
    suspend fun healthCheck(): HealthResponse
}

// ===================== RETROFIT CLIENT SINGLETON =====================

object RetrofitClient {

    // ═══════════════════════════════════════════════════════════════
    // UPDATE THIS to your current ngrok URL every time you restart
    // ngrok. Both phones must use the SAME URL.
    //
    // Steps:
    //  1. Run:  uvicorn main:app --reload --port 8000
    //  2. Run:  ngrok http 8000
    //  3. Copy the https URL shown (e.g. https://abc123.ngrok-free.app)
    //  4. Paste it below, keeping the trailing slash
    //  5. Rebuild and install the APK on both phones
    // ═══════════════════════════════════════════════════════════════
    private const val BASE_URL = "https://unbanished-joshua-comfy.ngrok-free.dev/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("User-Agent", "RouteTrack-Android/1.0")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    suspend fun warmUp() {
        try {
            android.util.Log.d("RetrofitClient", "Checking server connectivity...")
            instance.healthCheck()
            android.util.Log.d("RetrofitClient", "Server is reachable!")
        } catch (e: Exception) {
            android.util.Log.w("RetrofitClient", "Server check failed: ${e.message}")
        }
    }
}