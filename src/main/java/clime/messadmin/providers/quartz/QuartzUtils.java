/**
 *
 */
package clime.messadmin.providers.quartz;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

import javax.servlet.ServletContext;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.InterruptableJob;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.PersistJobDataAfterExecution;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.SchedulerRepository;

/**
 * @author C&eacute;drik LIME
 */
final class QuartzUtils {

	// Quartz 2.1
	private static transient Class<?> dailyTimeIntervalTriggerClass;
	private static transient Method dailyTimeIntervalTrigger_getRepeatInterval;
	private static transient Method dailyTimeIntervalTrigger_getRepeatIntervalUnit;
	private static transient Method dailyTimeIntervalTrigger_getRepeatCount;
	private static transient Method dailyTimeIntervalTrigger_getStartTimeOfDay;
	private static transient Method dailyTimeIntervalTrigger_getEndTimeOfDay;
	private static transient Method dailyTimeIntervalTrigger_getTimesTriggered;
	private static transient Method dailyTimeIntervalTrigger_getDaysOfWeek;

	static {
		// @since Quartz 2.1
		try {
			dailyTimeIntervalTriggerClass = Class.forName("org.quartz.DailyTimeIntervalTrigger");//$NON-NLS-1$
			dailyTimeIntervalTrigger_getRepeatInterval = dailyTimeIntervalTriggerClass.getMethod("getRepeatInterval");//$NON-NLS-1$
			dailyTimeIntervalTrigger_getRepeatIntervalUnit = dailyTimeIntervalTriggerClass.getMethod("getRepeatIntervalUnit");//$NON-NLS-1$
			dailyTimeIntervalTrigger_getRepeatCount = dailyTimeIntervalTriggerClass.getMethod("getRepeatCount");//$NON-NLS-1$
			dailyTimeIntervalTrigger_getTimesTriggered = dailyTimeIntervalTriggerClass.getMethod("getTimesTriggered");//$NON-NLS-1$
			dailyTimeIntervalTrigger_getStartTimeOfDay = dailyTimeIntervalTriggerClass.getMethod("getStartTimeOfDay");//$NON-NLS-1$
			dailyTimeIntervalTrigger_getEndTimeOfDay = dailyTimeIntervalTriggerClass.getMethod("getEndTimeOfDay");//$NON-NLS-1$
			dailyTimeIntervalTrigger_getDaysOfWeek = dailyTimeIntervalTriggerClass.getMethod("getDaysOfWeek");//$NON-NLS-1$
		} catch (LinkageError e) {
		} catch (ClassNotFoundException e) {
		} catch (SecurityException e) {
		} catch (NoSuchMethodException e) {
		}
	}


	private QuartzUtils() {
	}


	public static String getUniqueIdentifier(Scheduler scheduler) {
		try {
			return scheduler.getSchedulerName() + "_$_" + scheduler.getSchedulerInstanceId();
		} catch (SchedulerException e) {
			throw (IllegalStateException) new IllegalStateException().initCause(e);
		}
	}

	public static Scheduler getSchedulerWithUID(String uid, ServletContext context) {
		// Spring
		Collection/*<SchedulerFactoryBean>*/ schedulerBeans = SpringQuartzUtils.getSchedulerFactoryBeans(context);
		if ( ! schedulerBeans.isEmpty()) {
			for (Object/*SchedulerFactoryBean*/ schedulerFactoryBean : schedulerBeans) {
				boolean schedulerBeanIsRunning = SpringQuartzUtils.isRunning(schedulerFactoryBean);//schedulerBean.isRunning()
//				if (schedulerBeanIsRunning) {// Spring 2.0
					Scheduler scheduler = SpringQuartzUtils.getScheduler(schedulerFactoryBean);// (Scheduler) schedulerBean.getObject();
					if (scheduler != null && uid.equals(QuartzUtils.getUniqueIdentifier(scheduler))) {
						return scheduler;
					}
//				}
			}
		}
		// Quartz
//		((SchedulerFactory) context.getAttribute(QuartzInitializerListener.QUARTZ_FACTORY_KEY)).getAllSchedulers()
//		==
//		SchedulerRepository.getInstance().lookupAll()
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			if (uid.equals(QuartzUtils.getUniqueIdentifier(scheduler))) {
				return scheduler;
			}
		}
		return null;
	}

	/**
	 * <p>
	 * Whether or not the <code>Job</code> implements the interface <code>{@link InterruptableJob}</code>.
	 * </p>
	 */
	public static boolean isInterruptable(JobDetail jobDetail) {
		Class<? extends Job> jobClass = jobDetail.getJobClass();
		if (jobClass == null) {
			return false;
		}
		return (InterruptableJob.class.isAssignableFrom(jobClass));
	}

	/*
The StatefulJob interface has been deprecated in favor of new class-level annotations for Job classes (using both annotations produces equivalent to that of the old StatefulJob interface):
@PersistJobDataAfterExecution - instructs the scheduler to re-store the Job's JobDataMap contents after execution completes.
@DisallowConcurrentExecution - instructs the scheduler to block other instances of the same job (by JobKey) from executing when one already is.
	 */


	/***********************************************************************/


	/**
	 * @since Quartz 2.1
	 */
	public static boolean isDailyTimeIntervalTrigger(Trigger trigger) {
		return dailyTimeIntervalTriggerClass != null && dailyTimeIntervalTriggerClass.isInstance(trigger);
	}

	/**
	 * @since Quartz 2.1
	 */
	private static Object getDailyTimeIntervalTrigger_invoke(Trigger trigger, Method method) {
		if (! isDailyTimeIntervalTrigger(trigger) || method == null) {
			throw new IllegalArgumentException(String.valueOf(trigger));
		}
		try {
			return method.invoke(trigger);
		} catch (Exception ignore) {
			return null;
		}
	}

	/**
	 * @since Quartz 2.1
	 */
	public static String getDailyTimeIntervalTrigger_RepeatInterval(Trigger trigger) {
		return (String) getDailyTimeIntervalTrigger_invoke(trigger, dailyTimeIntervalTrigger_getRepeatInterval);
	}

	/**
	 * @since Quartz 2.1
	 */
	public static /*IntervalUnit*/Object getDailyTimeIntervalTrigger_RepeatIntervalUnit(Trigger trigger) {
		return getDailyTimeIntervalTrigger_invoke(trigger, dailyTimeIntervalTrigger_getRepeatIntervalUnit);
	}

	/**
	 * @since Quartz 2.1
	 */
	public static Integer getDailyTimeIntervalTrigger_RepeatCount(Trigger trigger) {
		return (Integer) getDailyTimeIntervalTrigger_invoke(trigger, dailyTimeIntervalTrigger_getRepeatCount);
	}

	/**
	 * @since Quartz 2.1
	 */
	public static Integer getDailyTimeIntervalTrigger_TimesTriggered(Trigger trigger) {
		return (Integer) getDailyTimeIntervalTrigger_invoke(trigger, dailyTimeIntervalTrigger_getTimesTriggered);
	}

	/**
	 * @since Quartz 2.1
	 */
	public static /*TimeOfDay*/Object getDailyTimeIntervalTrigger_StartTimeOfDay(Trigger trigger) {
		return getDailyTimeIntervalTrigger_invoke(trigger, dailyTimeIntervalTrigger_getStartTimeOfDay);
	}

	/**
	 * @since Quartz 2.1
	 */
	public static /*TimeOfDay*/Object getDailyTimeIntervalTrigger_EndTimeOfDay(Trigger trigger) {
		return getDailyTimeIntervalTrigger_invoke(trigger, dailyTimeIntervalTrigger_getEndTimeOfDay);
	}

	/**
	 * @since Quartz 2.1
	 */
	@SuppressWarnings("unchecked")
	public static Set<Integer> getDailyTimeIntervalTrigger_DaysOfWeek(Trigger trigger) {
		return (Set<Integer>) getDailyTimeIntervalTrigger_invoke(trigger, dailyTimeIntervalTrigger_getDaysOfWeek);
	}

}
