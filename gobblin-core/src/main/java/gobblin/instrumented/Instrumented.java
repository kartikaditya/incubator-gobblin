/*
 * (c) 2014 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.instrumented;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import gobblin.constructs.Constructs;
import gobblin.metrics.GobblinMetrics;
import gobblin.metrics.GobblinMetricsRegistry;
import gobblin.configuration.State;
import gobblin.converter.Converter;
import gobblin.fork.ForkOperator;
import gobblin.metrics.MetricContext;
import gobblin.metrics.Tag;
import gobblin.qualitychecker.row.RowLevelPolicy;
import gobblin.source.extractor.Extractor;
import gobblin.writer.DataWriter;


/**
 * Provides simple instrumentation for gobblin-core components.
 *
 * Creates {@link gobblin.metrics.MetricContext}. Tries to read the name of the parent context
 * from key "metrics.context.name" at state, and tries to get the parent context by name from
 * the {@link gobblin.metrics.MetricContext} registry (the parent context must be registered).
 *
 * Automatically adds two tags to the inner context:
 * - component: attempts to determine which component type within gobblin-api generated this instace.
 * - class: the specific class of the object that generated this instace of Instrumented
 *
 */
public class Instrumented implements Instrumentable, Closeable {

  public static final String METRIC_CONTEXT_NAME_KEY = "metrics.context.name";
  protected final MetricContext metricContext;

  /**
   * Gets metric context with no additional tags.
   * See {@link #getMetricContext(State, Class, List)}
   */
  public static MetricContext getMetricContext(State state, Class<?> klazz) {
    return getMetricContext(state, klazz, new ArrayList<Tag<?>>());
  }

  /**
   * Generate a {@link gobblin.metrics.MetricContext} to be used by an object needing instrumentation.
   * This method will read the property "metrics.context.name" from the input State, and will attempt
   * to find a MetricContext with that name in the global instance of {@link gobblin.metrics.GobblinMetricsRegistry}.
   * If it succeeds, the generated MetricContext will be a child of the retrieved Context, otherwise it will
   * be a parent-less context.
   * The method will automatically add two tags to the context:
   *  - construct will contain the name of the {@link gobblin.constructs.Constructs} that klazz represents.
   *  - class will contain the canonical name of the input class.
   *
   * @param state {@link gobblin.configuration.State} used to find the parent MetricContext.
   * @param klazz Class of the object needing instrumentation.
   * @param tags Additional tags to add to the returned context.
   * @return A {@link gobblin.metrics.MetricContext} with the appropriate tags and parent.
   */
  public static MetricContext getMetricContext(State state, Class<?> klazz, List<Tag<?>> tags) {
    int randomId = (new Random()).nextInt();

    Constructs construct = null;
    if(Converter.class.isAssignableFrom(klazz)) {
      construct = Constructs.CONVERTER;
    } else if(ForkOperator.class.isAssignableFrom(klazz)) {
      construct = Constructs.FORK_OPERATOR;
    } else if(RowLevelPolicy.class.isAssignableFrom(klazz)) {
      construct = Constructs.ROW_QUALITY_CHECKER;
    } else if(Extractor.class.isAssignableFrom(klazz)) {
      construct = Constructs.EXTRACTOR;
    } else if(DataWriter.class.isAssignableFrom(klazz)) {
      construct = Constructs.WRITER;
    }

    List<Tag<?>> generatedTags = new ArrayList<Tag<?>>();
    if (construct != null) {
      generatedTags.add(new Tag<String>("construct", construct.toString()));
    }
    generatedTags.add(new Tag<String>("class", klazz.getCanonicalName()));

    GobblinMetrics gobblinMetrics;
    MetricContext.Builder builder = state.contains(METRIC_CONTEXT_NAME_KEY) &&
        (gobblinMetrics = GobblinMetricsRegistry.getInstance().get(state.getProp(METRIC_CONTEXT_NAME_KEY))) != null ?
        gobblinMetrics.getMetricContext().childBuilder(klazz.getCanonicalName() + "." + randomId) :
        MetricContext.builder(klazz.getCanonicalName() + "." + randomId);
    return builder.
        addTags(generatedTags).
        addTags(tags).
        build();
  }

  @SuppressWarnings("unchecked")
  public Instrumented(State state, Class<?> klazz) {
    this.metricContext = getMetricContext(state, klazz);
  }

  @SuppressWarnings("unchecked")
  public Instrumented(State state, Class<?> klazz, List<Tag<?>> tags) {
    this.metricContext = getMetricContext(state, klazz, tags);
  }

  @Override
  public MetricContext getMetricContext() {
    return this.metricContext;
  }

  @Override
  public void close()
      throws IOException {
    this.metricContext.close();
  }

}
