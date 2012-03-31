/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2009, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.classic.spi;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.MDCAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * The internal representation of logging events. When an affirmative decision
 * is made to log then a <code>LoggingEvent</code> instance is created. This
 * instance is passed around to the different logback-classic components.
 * 
 * <p>
 * Writers of logback-classic components such as appenders should be aware of
 * that some of the LoggingEvent fields are initialized lazily. Therefore, an
 * appender wishing to output data to be later correctly read by a receiver,
 * must initialize "lazy" fields prior to writing them out. See the
 * {@link #prepareForDeferredProcessing()} method for the exact list.
 * </p>
 *
 * @author Ceki G&uuml;lc&uuml;
 * @author S&eacute;bastien Pennec
 * @author Chris Dolan -- monkeypatching logback 0.9.28 to work around http://jira.qos.ch/browse/LBCLASSIC-275 and https://ops4j1.jira.com/browse/PAXLOGGING-103
 *
 * TODO: remove this class from pax-logging-logback and instead simply use the version from Logback 0.9.30 or later
 *     which has a fix for LBCLASSIC-275
 */
@SuppressWarnings({ "unchecked" }) // because this is copy-paste code
public class LoggingEvent implements ILoggingEvent {

  /**
   * Fully qualified name of the calling Logger class. This field does not
   * survive serialization.
   * 
   * <p>
   * Note that the getCallerInformation() method relies on this fact.
   */
  transient String fqnOfLoggerClass;

  /**
   * The name of thread in which this logging event was generated.
   */
  private String threadName;

  private String loggerName;
  private LoggerContext loggerContext;
  private LoggerContextVO loggerContextVO;

  /**
   * Level of logging event.
   * 
   * <p>
   * This field should not be accessed directly. You shoud use the
   * {@link #getLevel} method instead.
   * </p>
   * 
   */
  private transient Level level;

  private String message;

  // we gain significant space at serialization time by marking
  // formattedMessage as transient and constructing it lazily in
  // getFormmatedMessage()
  private transient String formattedMessage;

  private transient Object[] argumentArray;

  private ThrowableProxy throwableProxy;

  private StackTraceElement[] callerDataArray;

  private Marker marker;

  private Map<String, String> mdcPropertyMap;
  private static final Map<String, String> CACHED_NULL_MAP = new HashMap<String, String>();

  /**
   * The number of milliseconds elapsed from 1/1/1970 until logging event was
   * created.
   */
  private long timeStamp;

  public LoggingEvent() {
  }

  public LoggingEvent(String fqcn, Logger logger, Level level, String message,
                      Throwable throwable, Object[] argArray) {
    this.fqnOfLoggerClass = fqcn;
    this.loggerName = logger.getName();
    this.loggerContext = logger.getLoggerContext();
    this.loggerContextVO = loggerContext.getLoggerContextRemoteView();
    this.level = level;

    this.message = message;

    FormattingTuple ft = MessageFormatter.arrayFormat(message, argArray);
    formattedMessage = ft.getMessage();

    if (throwable == null) {
      argumentArray = ft.getArgArray();
      throwable = ft.getThrowable();
    } else {
      this.argumentArray = argArray;
    }

    if (throwable != null) {
      this.throwableProxy = new ThrowableProxy(throwable);
      LoggerContext lc = logger.getLoggerContext();
      if (lc.isPackagingDataEnabled()) {
        this.throwableProxy.calculatePackagingData();
      }
    }

    timeStamp = System.currentTimeMillis();
  }

  public void setArgumentArray(Object[] argArray) {
    if (this.argumentArray != null) {
      throw new IllegalStateException("argArray has been already set");
    }
    this.argumentArray = argArray;
  }

  public Object[] getArgumentArray() {
    return this.argumentArray;
  }

  public Level getLevel() {
    return level;
  }

  public String getLoggerName() {
    return loggerName;
  }

  public void setLoggerName(String loggerName) {
    this.loggerName = loggerName;
  }

  public String getThreadName() {
    if (threadName == null) {
      threadName = (Thread.currentThread()).getName();
    }
    return threadName;
  }

  /**
   * @param threadName
   *          The threadName to set.
   * @throws IllegalStateException
   *           If threadName has been already set.
   */
  public void setThreadName(String threadName) throws IllegalStateException {
    if (this.threadName != null) {
      throw new IllegalStateException("threadName has been already set");
    }
    this.threadName = threadName;
  }

  /**
   * Returns the throwable information contained within this event. May be
   * <code>null</code> if there is no such information.
   */
  public IThrowableProxy getThrowableProxy() {
    return throwableProxy;
  }

  /**
   * Set this event's throwable information.
   */
  public void setThrowableProxy(ThrowableProxy tp) {
    if (throwableProxy != null) {
      throw new IllegalStateException("ThrowableProxy has been already set.");
    } else {
      throwableProxy = tp;
    }
  }

  /**
   * This method should be called prior to serializing an event. It should also
   * be called when using asynchronous or deferred logging.
   * 
   * <p>
   * Note that due to performance concerns, this method does NOT extract caller
   * data. It is the responsibility of the caller to extract caller information.
   */
  public void prepareForDeferredProcessing() {
    this.getFormattedMessage();
    this.getThreadName();
    // fixes http://jira.qos.ch/browse/LBCLASSIC-104
    if (mdcPropertyMap == null) {
      mdcPropertyMap = MDC.getCopyOfContextMap();
      if (mdcPropertyMap == null)
        mdcPropertyMap = CACHED_NULL_MAP;
    }
  }

  public LoggerContextVO getLoggerContextVO() {
    return loggerContextVO;
  }

  public void setLoggerContextRemoteView(LoggerContextVO loggerContextVO) {
    this.loggerContextVO = loggerContextVO;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    if (this.message != null) {
      throw new IllegalStateException(
          "The message for this event has been set already.");
    }
    this.message = message;
  }

  public long getTimeStamp() {
    return timeStamp;
  }

  public void setTimeStamp(long timeStamp) {
    this.timeStamp = timeStamp;
  }

  public void setLevel(Level level) {
    if (this.level != null) {
      throw new IllegalStateException(
          "The level has been already set for this event.");
    }
    this.level = level;
  }

  /**
   * Get the caller information for this logging event. If caller information is
   * null at the time of its invocation, this method extracts location
   * information. The collected information is cached for future use.
   * 
   * <p>
   * Note that after serialization it is impossible to correctly extract caller
   * information.
   * </p>
   */
  public StackTraceElement[] getCallerData() {
    if (callerDataArray == null) {
      callerDataArray = CallerData.extract(new Throwable(), fqnOfLoggerClass,
          loggerContext.getMaxCallerDataDepth());
    }
    return callerDataArray;
  }

  public boolean hasCallerData() {
    return (callerDataArray != null);
  }

  public void setCallerData(StackTraceElement[] callerDataArray) {
    this.callerDataArray = callerDataArray;
  }

  public Marker getMarker() {
    return marker;
  }

  public void setMarker(Marker marker) {
    if (this.marker != null) {
      throw new IllegalStateException(
          "The marker has been already set for this event.");
    }
    this.marker = marker;
  }

  public long getContextBirthTime() {
    return loggerContextVO.getBirthTime();
  }

  // computer formatted lazy as suggested in
  // http://jira.qos.ch/browse/LBCLASSIC-47
  public String getFormattedMessage() {
    if (formattedMessage != null) {
      return formattedMessage;
    }
    if (argumentArray != null) {
      formattedMessage = MessageFormatter.arrayFormat(message, argumentArray)
          .getMessage();
    } else {
      formattedMessage = message;
    }

    return formattedMessage;
  }

  public Map<String, String> getMDCPropertyMap() {
    if (mdcPropertyMap == null) {
      MDCAdapter mdc = MDC.getMDCAdapter();
      if (mdc instanceof LogbackMDCAdapter)
        return ((LogbackMDCAdapter)mdc).getPropertyMap();
      else {
        mdcPropertyMap = mdc.getCopyOfContextMap();
        if (mdcPropertyMap == null)
          mdcPropertyMap = CACHED_NULL_MAP;
      }
    }
    return mdcPropertyMap == CACHED_NULL_MAP ? null : mdcPropertyMap;
  }

  public Map<String, String> getMdc() {
    return getMDCPropertyMap();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    sb.append(level).append("] ");
    sb.append(getFormattedMessage());
    return sb.toString();
  }

}