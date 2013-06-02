/**
 *
 */
package clime.messadmin.providers.quartz;

import static org.quartz.JobKey.jobKey;
import static org.quartz.TriggerKey.triggerKey;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.SchedulerRepository;

import clime.messadmin.admin.AdminActionProvider;
import clime.messadmin.admin.BaseAdminActionWithContext;
import clime.messadmin.i18n.I18NSupport;
import clime.messadmin.model.Server;
import clime.messadmin.providers.spi.ApplicationDataProvider;
import clime.messadmin.utils.Integers;
import clime.messadmin.utils.StringUtils;

/**
 * Displays Quartz 2.x {@link org.quartz.Scheduler}'s statistics.<br />
 * This implementation uses Spring to fetch the {@link org.springframework.scheduling.quartz.SchedulerFactoryBean SchedulerFactoryBean},
 * and also lookups Quartz' {@link SchedulerRepository}.
 *
 * TODO pauseAll(), resumeAll(), pause/resume JobGroup/TriggerGroup
 * TODO see org.quartz.plugins.history.LoggingJobHistoryPlugin and org.quartz.plugins.history.LoggingTriggerHistoryPlugin for creating a time log of events
 *
 * @since MessAdmin 5.0
 * @author C&eacute;drik LIME
 */
public class QuartzStatistics extends BaseAdminActionWithContext implements ApplicationDataProvider, AdminActionProvider {
	private static final String BUNDLE_NAME = QuartzStatistics.class.getName();

	public static final String ACTION_ID = "quartz2";//$NON-NLS-1$
	public static final String PARAM_QUARTZ_ACTION_NAME = "quartzAction";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_SCHEDULER_STANDBY = "schedulerPause";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_SCHEDULER_START   = "schedulerResume";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_SCHEDULER_CLEAR   = "schedulerClear";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_TRIGGER_PAUSE      = "triggerPause";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_TRIGGER_RESUME     = "triggerResume";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_TRIGGER_UNSCHEDULE = "triggerUnschedule";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_JOB_INTERRUPT        = "jobPause";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_JOB_PAUSE            = "jobPause";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_JOB_RESUME           = "jobResume";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_JOB_DELETE           = "jobDelete";//$NON-NLS-1$
	public static final String QUARTZ_ACTION_JOB_TRIGGER          = "jobTrigger";//$NON-NLS-1$
	public static final String PARAM_SCHEDULER_UID = "schedulerUID";//$NON-NLS-1$
	public static final String PARAM_JOB_NAME      = "jobName";//$NON-NLS-1$
	public static final String PARAM_JOB_GROUP     = "jobGroup";//$NON-NLS-1$
	public static final String PARAM_TRIGGER_NAME  = "triggerName";//$NON-NLS-1$
	public static final String PARAM_TRIGGER_GROUP = "triggerGroup";//$NON-NLS-1$

	/**
	 *
	 */
	public QuartzStatistics() {
		super();
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	@Override
	public int getPriority() {
		return 1000;
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public String getApplicationDataTitle(ServletContext context) {
		final ClassLoader cl = Server.getInstance().getApplication(context).getApplicationInfo().getClassLoader();
		Set<Object> schedulers = new HashSet<Object>();
		schedulers.addAll(SpringQuartzUtils.getSchedulerFactoryBeans(context));
		schedulers.addAll(SchedulerRepository.getInstance().lookupAll());
		return I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "title", new Object[] {//$NON-NLS-1$
				Integers.valueOf(schedulers.size())
		});
	}

	/** {@inheritDoc} */
	public String getXHTMLApplicationData(ServletContext context) {
		final StringBuffer out = new StringBuffer(1024);
		final Set<Scheduler> visited = new HashSet<Scheduler>();

		// Spring
		Collection/*<SchedulerFactoryBean>*/ schedulerBeans = SpringQuartzUtils.getSchedulerFactoryBeans(context);
		if ( ! schedulerBeans.isEmpty()) {
			for (Iterator/*<SchedulerFactoryBean>*/ it = schedulerBeans.iterator(); it.hasNext();) {
				Object/*SchedulerFactoryBean*/ schedulerFactoryBean = it.next();
				boolean schedulerBeanIsRunning = SpringQuartzUtils.isRunning(schedulerFactoryBean);//schedulerBean.isRunning()
//				if (schedulerBeanIsRunning) {// Spring 2.0
					Scheduler scheduler = SpringQuartzUtils.getScheduler(schedulerFactoryBean);// (Scheduler) schedulerBean.getObject();
					if (scheduler != null) {
						visited.add(scheduler);
						dump(out, context, scheduler);
					}
//				} else {
//					out.append("SchedulerFactoryBean ").append(schedulerFactoryBean).append(" is not running.");
//				}
				out.append("<br/>\n");
			}
		}

		// Spring's SchedulerFactoryBean#exposeSchedulerInRepository defaults to false
		// Lookup additional Schedulers in Quartz' SchedulerRepository

		// Quartz
//		((SchedulerFactory) context.getAttribute(QuartzInitializerListener.QUARTZ_FACTORY_KEY)).getAllSchedulers()
//		==
//		SchedulerRepository.getInstance().lookupAll()
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			if (! visited.contains(scheduler)) {
				visited.add(scheduler);
				dump(out, context, scheduler);
				out.append("<br/>\n");
			}
		}

		visited.clear();
		return out.toString();
	}

	private void dump(StringBuffer out, ServletContext context, Scheduler scheduler) {
		try {
			// scheduler Summary
			out.append("<pre>").append(StringUtils.escapeXml(scheduler.getMetaData().getSummary())).append("</pre>");//$NON-NLS-1$//$NON-NLS-2$
			if (! scheduler.isStarted() || scheduler.isShutdown()) {
				// No need to display details
				return;
			}
			String urlPrefix = getUrlPrefix(context, scheduler).toString();
			if (scheduler.isInStandbyMode()) {
				//TODO -> gray all jobs/triggers, since no firing
				// link to "resume" start()
				String urlResume = urlPrefix + QUARTZ_ACTION_SCHEDULER_START;
				out.append(buildActionLink(urlResume, I18NSupport.getLocalizedMessage(BUNDLE_NAME, "action.scheduler.resume"), this));//$NON-NLS-1$
			} else {
				// link to "pause" standby()
				String urlPause = urlPrefix + QUARTZ_ACTION_SCHEDULER_STANDBY;
				out.append(buildActionLink(urlPause, I18NSupport.getLocalizedMessage(BUNDLE_NAME, "action.scheduler.pause"), this));//$NON-NLS-1$
			}
			out.append("&nbsp;");//$NON-NLS-1$
			// TODO link to shutdown()?
			// link to "clear" clear()
			String urlClear = urlPrefix + QUARTZ_ACTION_SCHEDULER_CLEAR;
			out.append(buildActionLink(urlClear, I18NSupport.getLocalizedMessage(BUNDLE_NAME, "action.scheduler.clear"), I18NSupport.getLocalizedMessage(BUNDLE_NAME, "action.scheduler.clear.confirmJS"), this));//$NON-NLS-1$//$NON-NLS-2$
			List<JobExecutionContext> currentlyExecutingJobs = scheduler.getCurrentlyExecutingJobs();
			// Triggers: scheduler.getTriggerGroupNames();
			new QuartzTriggerTable(context, this).getXHTMLData(out, scheduler);
			// Jobs: scheduler.getJobGroupNames();
			new QuartzJobTable(context, this).getXHTMLData(out, scheduler);
			// Calendars: scheduler.getCalendarNames();
			//scheduler.getGlobalJobListeners();
			//scheduler.getJobListenerNames();
			//scheduler.getGlobalTriggerListeners();
			//scheduler.getTriggerListenerNames();
			//scheduler.getSchedulerListeners();
		} catch (SchedulerException se) {
			throw new RuntimeException(se);
		}
	}

	protected static StringBuffer getUrlPrefix(ServletContext context, Scheduler scheduler) {
		try {
			StringBuffer urlPrefix = new StringBuffer(32).append('?').append(ACTION_PARAMETER_NAME).append('=').append(ACTION_ID)
				.append('&').append(CONTEXT_KEY).append('=').append(URLEncoder.encode(Server.getInstance().getApplication(context).getApplicationInfo().getInternalContextPath(), "UTF-8"))
				.append('&').append(PARAM_SCHEDULER_UID).append('=').append(URLEncoder.encode(QuartzUtils.getUniqueIdentifier(scheduler), "UTF-8"))
				.append('&').append(PARAM_QUARTZ_ACTION_NAME).append('=');
			return urlPrefix;
		} catch (UnsupportedEncodingException uue) {
			throw new RuntimeException(uue);
		}
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public String getActionID() {
		return ACTION_ID;
	}

	protected void displayXHTMLApplicationData(HttpServletRequest request, HttpServletResponse response, String context) throws ServletException, IOException {
		// ensure we get a GET
		if (METHOD_POST.equals(request.getMethod())) {
			sendRedirect(request, response);
			return;
		}
		// display a listing of all caches
		String data = getXHTMLApplicationData(getServletContext(context));
		setNoCache(response);
		PrintWriter out = response.getWriter();
		out.print(data);
		out.flush();
		out.close();
	}

	/** {@inheritDoc} */
	@Override
	public void serviceWithContext(HttpServletRequest request, HttpServletResponse response, String context) throws ServletException, IOException {
		String quartzAction = request.getParameter(PARAM_QUARTZ_ACTION_NAME);
		if (StringUtils.isBlank(quartzAction)) {
			displayXHTMLApplicationData(request, response, context);
			return;
		}
		String schedulerUID = request.getParameter(PARAM_SCHEDULER_UID);
		String jobName      = request.getParameter(PARAM_JOB_NAME);
		String jobGroup     = request.getParameter(PARAM_JOB_GROUP);
		String triggerName  = request.getParameter(PARAM_TRIGGER_NAME);
		String triggerGroup = request.getParameter(PARAM_TRIGGER_GROUP);
		if (StringUtils.isBlank(schedulerUID)) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, PARAM_SCHEDULER_UID + " parameter is required");
			return;
		} // else
		// get Scheduler to modify
		Scheduler scheduler = QuartzUtils.getSchedulerWithUID(schedulerUID, getServletContext(context));
		if (scheduler == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "Can not find Scheduler with UID " + schedulerUID);
			return;
		}
		try {
			if (QUARTZ_ACTION_SCHEDULER_START.equalsIgnoreCase(quartzAction)) {
				scheduler.start();
			} else if (QUARTZ_ACTION_SCHEDULER_STANDBY.equalsIgnoreCase(quartzAction)) {
				scheduler.standby();
			} else if (QUARTZ_ACTION_SCHEDULER_CLEAR.equalsIgnoreCase(quartzAction)) {
				scheduler.clear();
			} else if (QUARTZ_ACTION_TRIGGER_PAUSE.equalsIgnoreCase(quartzAction)) {
				scheduler.pauseTrigger(triggerKey(triggerName, triggerGroup));
			} else if (QUARTZ_ACTION_TRIGGER_RESUME.equalsIgnoreCase(quartzAction)) {
				scheduler.resumeTrigger(triggerKey(triggerName, triggerGroup));
			} else if (QUARTZ_ACTION_TRIGGER_UNSCHEDULE.equalsIgnoreCase(quartzAction)) {
				scheduler.unscheduleJob(triggerKey(triggerName, triggerGroup));
			} else if (QUARTZ_ACTION_JOB_TRIGGER.equalsIgnoreCase(quartzAction)) {
				// Quartz 2 API change
				//scheduler.triggerJobWithVolatileTrigger(jobName, jobGroup);
				scheduler.triggerJob(jobKey(jobName, jobGroup));
			} else if (QUARTZ_ACTION_JOB_INTERRUPT.equalsIgnoreCase(quartzAction)) {
				// {@linkplain org.quartz.InterruptableJob#interrupt() interrupt()} a running {@link org.quartz.InterruptableJob InterruptableJob}
				scheduler.interrupt(jobKey(jobName, jobGroup));
			} else if (QUARTZ_ACTION_JOB_PAUSE.equalsIgnoreCase(quartzAction)) {
				scheduler.pauseJob(jobKey(jobName, jobGroup));
			} else if (QUARTZ_ACTION_JOB_RESUME.equalsIgnoreCase(quartzAction)) {
				scheduler.resumeJob(jobKey(jobName, jobGroup));
			} else if (QUARTZ_ACTION_JOB_DELETE.equalsIgnoreCase(quartzAction)) {
				scheduler.deleteJob(jobKey(jobName, jobGroup));
			} else {
				response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, quartzAction + " value for parameter " + PARAM_QUARTZ_ACTION_NAME + " is unknown");
				return;
			}
		} catch (SchedulerException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.toString());
			return;
		}
		displayXHTMLApplicationData(request, response, context);
	}

}
