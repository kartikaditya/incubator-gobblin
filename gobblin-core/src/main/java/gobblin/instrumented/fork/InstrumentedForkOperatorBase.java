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

package gobblin.instrumented.fork;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.google.common.io.Closer;

import gobblin.configuration.WorkUnitState;
import gobblin.fork.ForkOperator;
import gobblin.instrumented.Instrumentable;
import gobblin.instrumented.Instrumented;
import gobblin.metrics.MetricContext;
import gobblin.metrics.MetricNames;


/**
 * package-private implementation of instrumentation for {@link gobblin.fork.ForkOperator}.
 * See {@link gobblin.instrumented.fork.InstrumentedForkOperator} for extensible class.
 */
abstract class InstrumentedForkOperatorBase<S, D> implements Instrumentable, ForkOperator<S, D> {

  protected MetricContext metricContext = new MetricContext.Builder("TMP").build();
  protected final Closer closer = Closer.create();
  // Initialize as dummy metrics to avoid null pointer exception if init was skipped
  protected Meter inputMeter = new Meter();
  protected Meter outputForks = new Meter();
  protected Timer forkOperatorTimer = new Timer();

  @Override
  public void init(WorkUnitState workUnitState)
      throws Exception {
    this.metricContext = closer.register(Instrumented.getMetricContext(workUnitState, this.getClass()));

    this.inputMeter = this.metricContext.meter(MetricNames.ForkOperator.RECORDS_IN);
    this.outputForks = this.metricContext.meter(MetricNames.ForkOperator.FORKS_OUT);
    this.forkOperatorTimer = this.metricContext.timer(MetricNames.ForkOperator.FORK_TIME);
  }

  @Override
  public List<Boolean> forkDataRecord(WorkUnitState workUnitState, D input) {
    long startTimeNanos = System.nanoTime();

    beforeFork(input);
    List<Boolean> result = forkDataRecordImpl(workUnitState, input);
    afterFork(result, startTimeNanos);

    return result;
  }

  /**
   * Called before forkDataRecord.
   * @param input
   */
  protected void beforeFork(D input) {
    this.inputMeter.mark();
  }

  /**
   * Called after forkDataRecord.
   * @param forks result from forkDataRecord.
   * @param startTimeNanos start time of forkDataRecord.
   */
  protected void afterFork(List<Boolean> forks, long startTimeNanos) {
    int forksGenerated = 0;
    for (Boolean fork : forks) {
      forksGenerated += fork ? 1 : 0;
    }
    this.outputForks.mark(forksGenerated);
    this.forkOperatorTimer.update(System.nanoTime() - startTimeNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Subclasses should implement this instead of {@link gobblin.fork.ForkOperator#forkDataRecord}.
   */
  public abstract List<Boolean> forkDataRecordImpl(WorkUnitState workUnitState, D input);

  @Override
  public MetricContext getMetricContext() {
    return this.metricContext;
  }

  @Override
  public void close()
      throws IOException {
    closer.close();
  }
}
