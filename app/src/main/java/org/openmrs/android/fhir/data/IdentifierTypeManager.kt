import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.openmrs.android.fhir.FhirApplication
import org.openmrs.android.fhir.ServerConstants.REST_BASE_URL
import org.openmrs.android.fhir.auth.dataStore
import org.openmrs.android.fhir.data.PreferenceKeys
import org.openmrs.android.fhir.data.database.model.IdentifierType

class IdentifierTypeManager private constructor(private val context: Context) {

    private var restApiClient: RestApiManager = FhirApplication.restApiClient(context)
    private val database = FhirApplication.roomDatabase(context)

    companion object {
        @SuppressLint("StaticFieldLeak")
        private lateinit var instance: IdentifierTypeManager
        private const val ENDPOINT = REST_BASE_URL + "patientidentifiertype?v=full"


        suspend fun fetchIdentifiers(context: Context) {
            withContext(Dispatchers.IO) {
                instance = IdentifierTypeManager(context)
                val newIdentifiers = instance.fetchIdentifierFromEndpoint()
                if(newIdentifiers!= null){
                    instance.storeIdentifiers(newIdentifiers)
                    instance.selectRequiredIdentifiers(newIdentifiers)
                }
            }
        }
    }

    private suspend fun storeIdentifiers(identifierTypes: List<IdentifierType>) {
        withContext(Dispatchers.IO){
            database.dao().insertAllIdentifierTypeModel(identifierTypes)
        }
    }

    private suspend fun selectRequiredIdentifiers(identifierTypes: List<IdentifierType>) {
        withContext(Dispatchers.IO){
            val selectedIdentifierTypes = instance.context.dataStore.data.first()[PreferenceKeys.SELECTED_IDENTIFIER_TYPES]
                ?.toMutableSet() ?: mutableSetOf()
            val requiredIdentifierTypes = identifierTypes.filter {
                it.required
            }
            if(requiredIdentifierTypes.isNotEmpty()){
                requiredIdentifierTypes.forEach {
                    selectedIdentifierTypes.add(it.uuid)
                }
            }
            instance.context.dataStore.edit { preferences ->
                preferences[PreferenceKeys.SELECTED_IDENTIFIER_TYPES] = selectedIdentifierTypes
            }
        }
    }

     suspend fun fetchIdentifierFromEndpoint(): List<IdentifierType>? {
        return withContext(Dispatchers.IO) {
            try {

                val requestBuilder = Request.Builder()
                    .url(ENDPOINT)
                    .get()
                val response = restApiClient.call(requestBuilder)

                if (response.isSuccessful) {
                    val resArray = mutableListOf<IdentifierType>()
                    val jsonResponse = JSONObject(response.body?.string() ?: "")
                    val identifiers = jsonResponse.getJSONArray("results")
                    for (i in 0..<identifiers.length()) {
                        val objects: JSONObject = identifiers.getJSONObject(i)
                        resArray.add(IdentifierType(
                            objects["uuid"].toString(),
                            objects["display"].toString(),
                            objects["uniquenessBehavior"].toString(),
                            objects.getBoolean("required")
                        ))
                    }
                    resArray.toList()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

}
