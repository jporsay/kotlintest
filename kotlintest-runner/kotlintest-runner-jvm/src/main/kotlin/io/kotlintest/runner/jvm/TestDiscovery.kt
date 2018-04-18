package io.kotlintest.runner.jvm

import io.kotlintest.Project
import io.kotlintest.Spec
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import java.net.URI
import io.kotlintest.extensions.DiscoveryExtension
import kotlin.reflect.KClass

/**
 * [DiscoveryRequest] is used to focus the scope of which classes to discover.
 *
 * @param uris a list of uris to act as a classpath roots to search
 * @param classNames if specified then these classes will be used instead of searching
 */
data class DiscoveryRequest(val uris: List<URI>, val classNames: List<String>)

/**
 * Scans for tests as specified by a [DiscoveryRequest].
 * [DiscoveryExtension] `afterScan` functions are applied after the scan is complete to
 * optionally filter the returned classes.
 */
object TestDiscovery {

  init {
    ReflectionsHelper.registerUrlTypes()
  }

  private fun reflections(uris: List<URI>): Reflections {

    val classOnly = { name: String? -> name?.endsWith(".class") ?: false }
    val excludeJDKPackages = FilterBuilder.parsePackages("-java, -javax, -sun, -com.sun")

    return Reflections(ConfigurationBuilder()
        .addUrls(uris.map { it.toURL() })
        .setExpandSuperTypes(true)
        .useParallelExecutor(2)
        .filterInputsBy(excludeJDKPackages.add(classOnly))
        .setScanners(SubTypesScanner()))
  }

  // returns all the locatable specs for the given uris
  private fun scan(uris: List<URI>): List<KClass<out Spec>> =
      reflections(uris)
          .getSubTypesOf(Spec::class.java)
          .map(Class<out Spec>::kotlin)
          // must filter out abstract to avoid the spec parent classes themselves
          .filter { !it.isAbstract }

  private fun loadClasses(classes: List<String>): List<KClass<out Spec>> =
      classes.map { Class.forName(it).kotlin }.filterIsInstance<KClass<out Spec>>()

  fun discover(request: DiscoveryRequest): List<KClass<out Spec>> {
    val classes = when {
      request.classNames.isNotEmpty() -> loadClasses(request.classNames)
      else -> scan(request.uris)
    }
    return Project.discoveryExtensions().fold(classes, { cl, ext -> ext.afterScan(cl) })
        .sortedBy { it.simpleName }
  }
}