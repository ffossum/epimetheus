package io.chrisdavenport.epimetheus

import cats.effect._
import cats.implicits._
import io.prometheus.client.{CollectorRegistry => JCollectorRegistry}

import java.io.StringWriter
import io.prometheus.client.exporter.common.TextFormat

/**
 * A [[CollectorRegistry]] is a registry of Collectors.
 * 
 * It represents the concurrently shared state which holds the information
 * of the metrics in question.
 * 
 * ==On Creation==
 * Due to how prometheus scraping occurs, only one CollectorRegistry is generally useful per 
 * application. There are generally 2 approaches.
 * 
 * 1. Create your own registry. Register Metrics with it. Expose that.
 *    Advantages: Full Control Of the Code
 * 2. Use the global [[CollectorRegistry.defaultRegistry defaultRegistry]]
 *    Advantages: Easier Interop with Java libraries that may not give
 *    an option for interaction with arbitrary CollectorRegistries.
 */
final class CollectorRegistry[F[_]: Sync] private(private val cr: JCollectorRegistry){
  /**
   * Register A [[Collector]] with this Collector Registory
   */
  def register(c: Collector): F[Unit] = 
    Sync[F].delay(cr.register(Collector.Unsafe.asJava(c)))

  /**
   * Unregister A [[Collector]] with this CollectorRegistry
   */
  def unregister(c: Collector): F[Unit] =
    Sync[F].delay(cr.unregister(Collector.Unsafe.asJava(c)))

  /**
   * Write out the text version 0.0.4 of the given MetricFamilySamples 
   * contained in the CollectorRegistry.
   * 
   * See http://prometheus.io/docs/instrumenting/exposition_formats/
   * for the output format specification
   */
  def write004: F[String] = Sync[F].delay {
    val writer = new StringWriter
    TextFormat.write004(writer, cr.metricFamilySamples)
    writer.toString
  }

}
object CollectorRegistry {

  /**
   * Build an Empty CollectorRegistry
   */
  def build[F[_]: Sync]: F[CollectorRegistry[F]] = 
    Sync[F].delay(new CollectorRegistry(new JCollectorRegistry))

  // Future Work: 
  // https://github.com/prometheus/client_java/commit/fcde9554d759c397775298c64f99c0c169c8111b
  // Makes available a default registration we can use to keep in line
  // with future changes.
  /**
   * Build a CollectorRegistry which has all of the [[Collector Collectors]] in
   * [[Collector.Defaults]] registered.
   */
  def buildWithDefaults[F[_]: Sync]: F[CollectorRegistry[F]] = 
    for {
      cr  <- build[F]
      bpe <- Collector.Defaults.BufferPoolsExports
      _   <- cr.register(bpe) 
      cle <- Collector.Defaults.ClassLoadingExports
      _   <- cr.register(cle)
      gce <- Collector.Defaults.GarbageCollectorExports
      _   <- cr.register(gce)
      mae <- Collector.Defaults.MemoryAllocationExports
      _   <- cr.register(mae)
      mpe <- Collector.Defaults.MemoryPoolsExports
      _   <- cr.register(mpe)
      se  <- Collector.Defaults.StandardExports
      _   <- cr.register(se)
      te  <- Collector.Defaults.ThreadExports
      _   <- cr.register(te)
      vie <- Collector.Defaults.VersionInfoExports
      _   <- cr.register(vie)
    } yield cr

  /**
   * Default Global Registry, what many Java interactions may
   * automatically register with, so may be necessary for those tools.
   */
  def defaultRegistry[F[_]: Sync]: CollectorRegistry[F] =
    Unsafe.fromJava(JCollectorRegistry.defaultRegistry)

  object Unsafe {
    def fromJava[F[_]: Sync](j: JCollectorRegistry): CollectorRegistry[F] = new CollectorRegistry[F](j)
    def asJava[F[_]](c: CollectorRegistry[F]): JCollectorRegistry = c.cr
  }
}