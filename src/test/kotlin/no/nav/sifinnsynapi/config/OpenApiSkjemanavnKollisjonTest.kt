package no.nav.sifinnsynapi.config

import com.fasterxml.jackson.databind.JavaType
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverterContextImpl
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media.Schema
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation

/**
 * swagger-core navngir skjemaer med klassens simple name uten pakke. To ulike klasser med samme navn i
 * samme OpenAPI-gruppe (f.eks. via k9-format sin polymorfe Søknad) gjør at den ene overskriver den andre,
 * og typen forsvinner fra den genererte frontend-kontrakten. Løs med @Schema(name = "...") på klassen.
 */
class OpenApiSkjemanavnKollisjonTest {

    @Test
    fun `ingen OpenAPI-gruppe har skjemanavn som deles av flere klasser`() {
        val grupper = grupperFraSwaggerConfiguration()
        assertTrue(grupper.isNotEmpty(), "Fant ingen GroupedOpenApi-bønner i SwaggerConfiguration")

        val kollisjoner = grupper.associate { gruppe ->
            gruppe.group to skjemaklasserPerNavn(gruppe).filterValues { it.size > 1 }
        }
        val funnet = kollisjoner.flatMap { (gruppe, navn) -> navn.keys.map { gruppe to it } }.toSet()

        assertTrue(funnet.isEmpty()) {
            "Følgende skjemanavn brukes av flere klasser i samme OpenAPI-gruppe. " +
                "Gi klassene unike navn med @Schema(name = \"...\"):\n" +
                funnet.joinToString("\n") { (gruppe, navn) ->
                    "[$gruppe] $navn:\n    " + kollisjoner.getValue(gruppe).getValue(navn).joinToString("\n    ")
                }
        }
    }

    private fun grupperFraSwaggerConfiguration(): List<GroupedOpenApi> {
        val config = SwaggerConfiguration(authorizationUrl = "", tokenUrl = "", apiScope = "")
        return SwaggerConfiguration::class.java.declaredMethods
            .filter { it.returnType == GroupedOpenApi::class.java && it.parameterCount == 0 }
            .map { it.invoke(config) as GroupedOpenApi }
    }

    private fun skjemaklasserPerNavn(gruppe: GroupedOpenApi): Map<String, Set<String>> {
        val context = RegistrerendeContext()
        controllere(gruppe.packagesToScan)
            .flatMap { it.declaredMethods.asList() }
            .filter { AnnotatedElementUtils.hasAnnotation(it, RequestMapping::class.java) }
            .flatMap { rotTyper(it) }
            .forEach { context.resolve(AnnotatedType(it).resolveAsRef(true)) }
        return context.klasserPerNavn
    }

    private fun controllere(pakker: List<String>): List<Class<*>> {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
            .apply { addIncludeFilter(AnnotationTypeFilter(RestController::class.java)) }
        return pakker.flatMap { scanner.findCandidateComponents(it) }
            .map { Class.forName(it.beanClassName) }
            .distinct()
    }

    private fun rotTyper(metode: Method): List<Type> {
        val parametere = metode.parameters.toList()
        val continuation = parametere.lastOrNull()?.takeIf { it.type == Continuation::class.java }
        val returtype = (continuation?.parameterizedType as? ParameterizedType)?.actualTypeArguments?.first()
            ?: metode.genericReturnType
        val requestBodies = parametere
            .filter { it.isAnnotationPresent(RequestBody::class.java) }
            .map { it.parameterizedType }
        return (requestBodies + pakkUt(returtype))
            .filterNot { it == Void.TYPE || it == Void::class.java || it == Unit::class.java }
    }

    private fun pakkUt(type: Type): Type = when {
        type is WildcardType -> pakkUt(type.lowerBounds.firstOrNull() ?: type.upperBounds.first())
        type is ParameterizedType && HttpEntity::class.java.isAssignableFrom(type.rawType as Class<*>) ->
            pakkUt(type.actualTypeArguments.first())
        else -> type
    }

    private class RegistrerendeContext : ModelConverterContextImpl(ModelConverters.getInstance().converters) {
        val klasserPerNavn = sortedMapOf<String, MutableSet<String>>()

        override fun defineModel(name: String, model: Schema<*>?, type: AnnotatedType?, prevName: String?) {
            type?.type?.let {
                val javaType: JavaType = Json.mapper().constructType(it)
                klasserPerNavn.getOrPut(name) { sortedSetOf() }.add(javaType.rawClass.name)
            }
            super.defineModel(name, model, type, prevName)
        }
    }
}
