package com.twitter.finagle.stats

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util.Duration
import java.io.{File, FileOutputStream}
import scala.jdk.CollectionConverters.mapAsScalaMapConverter

/**
 * Writes metrics to a file every `period`, and uses histograms that are
 * `period` windowed. Note, this writes to the same metrics map as the
 * service-loaded [[MetricsStatsReceiver]] so [[MetricsExporter]] still
 * has a valid data source if this is the only [[StatsReceiver]] for integ
 * sims.
 */
class FileAggregatorStatsReceiver(dumpFile: File, period: Duration) extends StatsReceiverProxy {

  private[this] val formatter = StatsFormatter.default
  private[this] def factory(name: String, percentiles: IndexedSeq[Double]): MetricsHistogram =
    new MetricsBucketedHistogram(name, percentiles, period)

  private[this] val metrics = new Metrics(factory _, scopeSeparator())
  private[this] val fos = new FileOutputStream(dumpFile)
  private[this] val prettyWriter = {
    val mapper = new ObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.writer(new DefaultPrettyPrinter)
  }

  private[this] val timer = DefaultTimer
  private[this] val fileTask = timer.schedule(period) {
    dumpToFile()
  }

  def shutdown(): Unit = {
    fileTask.cancel()
    fos.flush()
    fos.close()
  }

  private[this] def dumpToFile(): Unit = {
    val gauges = metrics.gauges.asScala + ("timestamp" -> (System.currentTimeMillis(): Number))
    val counters = metrics.counters.asScala
    val histograms = metrics.histograms.asScala

    val stats = formatter(SampledValues(gauges, counters, histograms))
    val content = prettyWriter.writeValueAsString(stats)
    fos.write(content.getBytes)
    fos.flush()
  }

  protected val self: StatsReceiver = new MetricsStatsReceiver(metrics)
  override def toString: String = "FileAggregatorStatsReceiver"
}

