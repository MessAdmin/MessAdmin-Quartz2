/**
 *
 */
package clime.messadmin.providers.quartz;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletContext;

import org.quartz.CalendarIntervalTrigger;
import org.quartz.CronTrigger;
import org.quartz.DateBuilder.IntervalUnit;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import clime.messadmin.i18n.I18NSupport;
import clime.messadmin.providers.spi.BaseTabularDataProvider;
import clime.messadmin.providers.spi.DisplayProvider;
import clime.messadmin.utils.DateUtils;
import clime.messadmin.utils.Integers;
import clime.messadmin.utils.StringUtils;

/**
 * @author C&eacute;drik LIME
 */
abstract class AbstractQuartzTable extends BaseTabularDataProvider {
	private static final String UTF_8 = "UTF-8";//$NON-NLS-1$
	protected static final String BUNDLE_NAME = QuartzStatistics.class.getName();

	protected ServletContext servletContext;
	protected DisplayProvider displayProvider;

	/**
	 *
	 */
	public AbstractQuartzTable(ServletContext servletContext, DisplayProvider displayProvider) {
		super();
		this.servletContext = servletContext;
		this.displayProvider = displayProvider;
	}

	protected final String urlEncodeUTF8(String url) {
		try {
			return URLEncoder.encode(url, UTF_8);
		} catch (UnsupportedEncodingException uue) {
			throw new RuntimeException(uue);
		}
	}

	public StringBuffer getXHTMLData(StringBuffer buffer, Scheduler scheduler) {
		try {
			String[] labels = getTabularDataLabels();
			Object[][] values = getTabularData(scheduler);
			String tableId = "extraApplicationAttributesTable-"+getClass().getName()+'-'+StringUtils.escapeXml(QuartzUtils.getUniqueIdentifier(scheduler));//$NON-NLS-1$
			buildXHTML(buffer, labels, values, tableId, getTableCaption(scheduler));
		} catch (SchedulerException e) {
			return buffer.append(e);
		}
		return buffer;
	}

	protected abstract String getTableCaption(Scheduler scheduler);

	public abstract String[] getTabularDataLabels();

	public abstract Object[][] getTabularData(Scheduler scheduler) throws SchedulerException;

	protected String makeCheckBox(boolean active) {
		return "<input type=\"checkbox\" " + (active ? "checked=\"checked\"" : "") + " disabled=\"disabled\" readonly=\"readonly\" />";
	}

	protected boolean isCurrentlyExecuting(JobDetail jobDetail, List<JobExecutionContext> currentlyExecutingJobs) {
		for (JobExecutionContext jec : currentlyExecutingJobs) {
			if (jobDetail.equals(jec.getJobDetail())) {
				return true;
			}
		}
		return false;
	}

	protected String getJobExtraInfo(Scheduler scheduler, JobDetail jobDetail) {
		String result;
		String jobClass = jobDetail.getJobClass().toString();
		int nTriggers = 0;
		try {
			List<? extends Trigger> triggersOfJob = scheduler.getTriggersOfJob(jobDetail.getKey());
			nTriggers = triggersOfJob.size();
		} catch (SchedulerException ignore) {
		}
		result = I18NSupport.getLocalizedMessage(BUNDLE_NAME, "job.extraInfo",//$NON-NLS-1
				new Object[] {
			jobClass, Integers.valueOf(nTriggers)
		});
		return result;
	}

	protected String getTriggerExtraInfo(Trigger trigger) {
		String triggerClass = trigger.getClass().toString();
		String result = triggerClass;
		if (CronTrigger.class.isInstance(trigger)) {
			CronTrigger cTrigger = (CronTrigger) trigger;
			String cronExpression = cTrigger.getCronExpression();//cTrigger.getExpressionSummary();
			String timeZone = cTrigger.getTimeZone().getID();
			result = I18NSupport.getLocalizedMessage(BUNDLE_NAME, "trigger.extraInfo."+CronTrigger.class.getName(),//$NON-NLS-1
					new Object[] {
				triggerClass, cronExpression, timeZone
			});
		} else if (SimpleTrigger.class.isInstance(trigger)) {
			SimpleTrigger sTrigger = (SimpleTrigger) trigger;
			int repeatCount = sTrigger.getRepeatCount();
			long repeatInterval = sTrigger.getRepeatInterval();
			int timesTriggered = sTrigger.getTimesTriggered();
			result = I18NSupport.getLocalizedMessage(BUNDLE_NAME, "trigger.extraInfo."+SimpleTrigger.class.getName(),//$NON-NLS-1
					new Object[] {
				triggerClass, DateUtils.timeIntervalToFormattedString(repeatInterval), repeatCount, timesTriggered
			});
		} else if (CalendarIntervalTrigger.class.isInstance(trigger)) {
			CalendarIntervalTrigger ciTrigger = (CalendarIntervalTrigger) trigger;
			int repeatInterval = ciTrigger.getRepeatInterval();
			IntervalUnit repeatIntervalUnit = ciTrigger.getRepeatIntervalUnit();
			//String timeZone = ciTrigger.getTimeZone().getID(); //TODO Quartz 2.1
			int timesTriggered = ciTrigger.getTimesTriggered();
			result = I18NSupport.getLocalizedMessage(BUNDLE_NAME, "trigger.extraInfo."+"org.quartz.CalendarIntervalTrigger",//NON-NLS-2
						new Object[] {
					triggerClass, repeatInterval, repeatIntervalUnit
			});
		} else if (QuartzUtils.isDailyTimeIntervalTrigger(trigger)) {//@since Quartz 2.1
			Set<Integer> daysOfWeek = QuartzUtils.getDailyTimeIntervalTrigger_DaysOfWeek(trigger);
			Object startTimeOfDay = QuartzUtils.getDailyTimeIntervalTrigger_StartTimeOfDay(trigger);
			Object endTimeOfDay = QuartzUtils.getDailyTimeIntervalTrigger_EndTimeOfDay(trigger);
			String repeatInterval = QuartzUtils.getDailyTimeIntervalTrigger_RepeatInterval(trigger);
			Object repeatIntervalUnit = QuartzUtils.getDailyTimeIntervalTrigger_RepeatIntervalUnit(trigger);
			result = I18NSupport.getLocalizedMessage(BUNDLE_NAME, "trigger.extraInfo.DailyTimeIntervalTrigger",//$NON-NLS-1
					new Object[] {
				triggerClass,
				repeatInterval, repeatIntervalUnit,
				startTimeOfDay, endTimeOfDay,
				daysOfWeek
			});
		}
		// DateIntervalTrigger and NthIncludedDayTrigger are deprecated, and moved in the quartz-backward-compat package
		return result;
	}
}
