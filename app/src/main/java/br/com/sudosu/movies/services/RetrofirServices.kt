package br.com.sudosu.movies.services

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.multidex.BuildConfig
import br.com.sudosu.movies.ConstantStrings.ISO_FORMAT
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitServices private constructor(private val context: Context) {

    @PublishedApi
    internal lateinit var retrofit: Retrofit

    init {
        initRetrofit()
    }

    private fun initRetrofit() {
        Log.d("RETROFIT", "Retrofit initialized!")

        //Configura o cliente http
        val okHttpClient: OkHttpClient = OkHttpClient.Builder()
            .certificatePinner(
                CertificatePinner.Builder()
                .add("*.getnet.com.br", "sha256/dbazgBbDSniAzBgrcx2R1K+uLA+xL1DUAFB6iVE9fNI=")
                .build())
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        val gsonConfig = GsonBuilder()
            .setDateFormat(ISO_FORMAT)
            .create()

        //Cria a instancia do Retrofit
        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL_DEV)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gsonConfig))
            .build()
    }

    /**
     * Cria e retorna uma instancia de um servico do tipo T
     */
    inline fun <reified T> getService(): T {
        return retrofit.create(T::class.java)
    }

    companion object {
        val BASE_URL_DEV = "https://homologacao-monedd.softkuka.com.br/"
        val BASE_URL_PROD = "https://homologacao-monedd.softkuka.com.br/"
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: RetrofitServices? = null

        fun getInstance(): RetrofitServices {
            try {
                return INSTANCE!!
            } catch (e: NullPointerException) {
                throw NullPointerException("Retrofit Services was not initialized. You must call RetrofitServices.initialize() on your application before using.")
            }
        }

        fun initialize(applicationContext: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = RetrofitServices(applicationContext)
                }
            }
        }

        fun getBaseURL() = if (BuildConfig.DEBUG) BASE_URL_DEV else BASE_URL_PROD

    }
}


fun <T> Call<T>.executeCallFunction(): LiveData<ServiceResponse<T>> {

    val liveData = MutableLiveData<ServiceResponse<T>>()

    enqueue(object : Callback<T> {

        val MAX_RETRIES = 3
        var retryCount = 0

        override fun onResponse(call: Call<T>?, response: retrofit2.Response<T>?) {

            try {

                response?.errorBody()?.string()?.also { errorBody ->

                    try {
                        //Prioriza erro so servidor.
                        val error = JSONObject(errorBody)
                        val msg = if (error.has("message")){
                            error.getString("message")
                        }else{
                            null
                        }

                        liveData.postValue(ServiceResponse.moneddVendasError(response.code(), msg))
                    }catch (e: Exception){
                        //Se não conseguir repassar o erro do servidor, trata somo erro normal.
                        liveData.postValue(ServiceResponse.httpError(response.code()))
                    }

                    Log.e("ERROR BODY", errorBody)
                    return
                }

                response?.body()?.also {
                    liveData.postValue(ServiceResponse.success(data = it))
                    return
                }

                liveData.postValue(ServiceResponse())

            } catch (exception: JsonParseException) {
                liveData.postValue(
                    ServiceResponse.moneddVendasError(
                        null,
                        "Erro ao desserializar resposta."
                    )
                )
            }
        }


        override fun onFailure(call: Call<T>?, t: Throwable?) {

            Log.w("REST/FALHA", "Falha na requisição: " + t?.localizedMessage)

            //Se a call foi cancelada, nao da retry.
            if (call?.isCanceled == false) {
                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    Handler().postDelayed({ call.clone().enqueue(this) }, 1000 * 2)
                } else {
                    liveData.postValue(ServiceResponse.moneddVendasError(call.hashCode(), t?.localizedMessage ?: ""))
                }
            }
        }
    })

    return liveData
}