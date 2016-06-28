package com.harasoft.relaunch;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class TaskManager extends Activity {
	final String TAG = "TaskManager";
	//final int DIALOG_TASK_DETAILS = 1;
	//final int DIALOG_SERVICE_DETAILS = 2;

	String[] doNotKillLabels;
	String[] doNotKillNames;

	ReLaunchApp app;
	SharedPreferences prefs;
	PackageManager pm;
	int sortMethod;
	int sortCpu;
	int sortSize;
	int sortAbc;
	TextView title1;
	TextView title2;
	TextView tasks_title;
	TextView serv_title;

	Button sortSizeBtn;
	Button sortCpuBtn;
	Button sortAbcBtn;

	final int CPUUpdate_i = 5000;
	long cpuTotal = 0;
	long cpuTotal_prev = 0;
	long cpuIdle = 0;
	long cpuIdle_prev = 0;
	boolean CPUUpdate_active = false;
	Handler CPUUpdate_h = new Handler();
	Runnable CPUUpdate = new Runnable() {
		public void run() {
			CPUUpdate_proc();
		}
	};
	long CPUUsage;
	int memTotal;
	long memUsage;

	List<Integer> taskPids = new ArrayList<Integer>();
	List<Integer> servPids = new ArrayList<Integer>();
	ListView lv_t;
	TAdapter adapter_t;
	boolean addSView_t = true;
	ListView lv_s;
	SAdapter adapter_s;
	boolean addSView_s = true;

	// Process info
	static class ExtraInfo {
		String label;
		String name;
		Drawable icon;
	}

	static class PInfo {
		PInfo() {
			prev = 0;
			curr = 0;
			usage = 0;
			mem = 0;
		}

		boolean service;

		// Service specific
		int clients;
		String clientPackage;
		String clientLabel;
		String descr;

		// Task specific
		List<ExtraInfo> e;

		// General
		int uid;
		long prev;
		long curr;
		Integer usage;
		Integer mem;
		int imp;
		String label;
		String name;
		String extra;
		Drawable icon;
	}

	HashMap<Integer, PInfo> pinfo = new HashMap<Integer, PInfo>();

	// Temp data for async. task
	List<Integer> newTask;
	List<Integer> newServ;
	HashMap<Integer, PInfo> newPinfo;


	public class cpuComparator implements java.util.Comparator<Integer> {
		public int compare(Integer o1, Integer o2) {
			PInfo p1 = pinfo.get(o1);
			PInfo p2 = pinfo.get(o2);
			if (p1 == null) {
				if (p2 == null)
					return 0;
				else
					return 1;
			} else if (p2 == null)
				return -1;

			int rc = -p1.usage.compareTo(p2.usage);
			if (rc == 0)
				return -p1.mem.compareTo(p2.mem);
			else
				return rc;
		}
	}

	public class sizeComparator implements java.util.Comparator<Integer> {
		public int compare(Integer o1, Integer o2) {
			PInfo p1 = pinfo.get(o1);
			PInfo p2 = pinfo.get(o2);
			if (p1 == null) {
				if (p2 == null)
					return 0;
				else
					return 1;
			} else if (p2 == null)
				return -1;

			return -p1.mem.compareTo(p2.mem);
		}
	}

	public class abcComparator implements java.util.Comparator<Integer> {
		public int compare(Integer o1, Integer o2) {
			PInfo p1 = pinfo.get(o1);
			PInfo p2 = pinfo.get(o2);
			if (p1 == null) {
				if (p2 == null)
					return 0;
				else
					return 1;
			} else if (p2 == null)
				return -1;

			if (p1.label == null && p2.label == null) {
				if (p1.name == null && p2.name == null)
					return -p1.mem.compareTo(p2.mem);
				if (p1.name == null)
					return 1;
				if (p2.name == null)
					return -1;
				return p1.name.compareTo(p2.name);
			}
			if (p1.label == null)
				return 1;
			if (p2.label == null)
				return -1;
			return p1.label.compareTo(p2.label);
		}
	}

	private boolean sortLists() {
		if (sortMethod == sortCpu) {
			Collections.sort(taskPids, new cpuComparator());
			Collections.sort(servPids, new cpuComparator());
			return true;
		} else if (sortMethod == sortSize) {
			Collections.sort(taskPids, new sizeComparator());
			Collections.sort(servPids, new sizeComparator());
			return true;
		} else if (sortMethod == sortAbc) {
			Collections.sort(taskPids, new abcComparator());
			Collections.sort(servPids, new abcComparator());
			return true;
		}
		return false;
	}

	private void setSorting(int m) {
		sortMethod = m;
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt("sortMethod", sortMethod);
		editor.commit();

		if (m == sortCpu) {
			sortCpuBtn.setEnabled(false);
			sortSizeBtn.setEnabled(true);
			sortAbcBtn.setEnabled(true);
			if (sortLists()) {
				adapter_t.notifyDataSetChanged();
				adapter_s.notifyDataSetChanged();
			}
		} else if (m == sortSize) {
			sortCpuBtn.setEnabled(true);
			sortSizeBtn.setEnabled(false);
			sortAbcBtn.setEnabled(true);
			if (sortLists()) {
				adapter_t.notifyDataSetChanged();
				adapter_s.notifyDataSetChanged();
			}
		} else if (m == sortAbc) {
			sortCpuBtn.setEnabled(true);
			sortSizeBtn.setEnabled(true);
			sortAbcBtn.setEnabled(false);
			if (sortLists()) {
				adapter_t.notifyDataSetChanged();
				adapter_s.notifyDataSetChanged();
			}
		}
	}

	private void stopCPUUpdate() {
		CPUUpdate_active = false;
		CPUUpdate_h.removeCallbacks(CPUUpdate);
	}

	private void startCPUUpdate() {
		if (!CPUUpdate_active) {
			CPUUpdate_active = true;
			CPUUpdate_proc();
		}
	}

	private void CPUUpdate_proc() {
		newTask = new ArrayList<Integer>();
		newServ = new ArrayList<Integer>();
		newPinfo = new HashMap<Integer, PInfo>();

		createAsyncTask().execute(false);
	}

	public AsyncTask<Boolean, Integer, String> createAsyncTask() {
		return new AsyncTask<Boolean, Integer, String>() {

			@Override
			protected String doInBackground(Boolean... params) {

				/*
				 * New services list
				 */
				ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
				List<ActivityManager.RunningServiceInfo> l2 = am
						.getRunningServices(1024);
				if (l2 != null) {
					int[] pids = new int[l2.size()];
					for (int i = 0; i < l2.size(); ++i)
						pids[i] = l2.get(i).pid;
					Debug.MemoryInfo[] mis = am.getProcessMemoryInfo(pids);

					for (int i = 0; i < l2.size(); ++i) {
						ActivityManager.RunningServiceInfo si = l2.get(i);
						PInfo p = new PInfo();

						String fs = "";
						p.imp = 3;
						if ((si.flags & ActivityManager.RunningServiceInfo.FLAG_FOREGROUND) != 0) {
							p.imp = 1;
							// no localization? correct me
							fs = fs.equals("") ? "FOREGROUND"
									: (fs + ",FOREGROUND");
						}
						if ((si.flags & ActivityManager.RunningServiceInfo.FLAG_PERSISTENT_PROCESS) != 0) {
							p.imp = 1;
							// no localization? correct me
							fs = fs.equals("") ? "PERSISTENT"
									: (fs + ",PERSISTENT");
						}
						if ((si.flags & ActivityManager.RunningServiceInfo.FLAG_STARTED) != 0) {
							p.imp = 2;
							// no localization? correct me
							fs = fs.equals("") ? "STARTED" : (fs + ",STARTED");
						}
						if ((si.flags & ActivityManager.RunningServiceInfo.FLAG_SYSTEM_PROCESS) != 0) {
							p.imp = 1;
							// no localization? correct me
							fs = fs.equals("") ? "SYSTEM" : (fs + ",SYSTEM");
						}

						if (newPinfo.containsKey(pids[i]))
							continue; // This service is already in services
										// list

						p.service = true;
						p.clients = si.clientCount;
						p.uid = si.uid;
						p.name = si.process;
						p.clientPackage = si.clientPackage;
						p.clientLabel = (si.clientLabel == 0) ? ""
								: getResources().getString(si.clientLabel);
						p.descr = si.service.toShortString();
						p.extra = fs;
						p.e = new ArrayList<ExtraInfo>();
						p.mem = mis[i].otherPrivateDirty
								+ mis[i].nativePrivateDirty
								+ mis[i].dalvikSharedDirty;
						try {
							ApplicationInfo ai = pm.getApplicationInfo(p.name,
									0);
							p.icon = pm.getApplicationIcon(ai.packageName);
							p.label = pm.getApplicationLabel(ai).toString();
						} catch (PackageManager.NameNotFoundException e) {
							p.icon = null;
							p.label = "";
						}
						newPinfo.put(pids[i], p);
						newServ.add(pids[i]);
					}
				}

				/*
				 * New tasks list
				 */
				List<ActivityManager.RunningAppProcessInfo> l1 = am
						.getRunningAppProcesses();
				if (l1 != null) {
					int[] pids = new int[l1.size()];
					for (int i = 0; i < l1.size(); i++)
						pids[i] = l1.get(i).pid;
					Debug.MemoryInfo[] mis = am.getProcessMemoryInfo(pids);
					for (int i = 0; i < l1.size(); i++) {
						ActivityManager.RunningAppProcessInfo pi = l1.get(i);
						PInfo p = new PInfo();

						String is = "";
						switch (pi.importance) {
						case ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND:
							p.imp = 2;
							// no localization? correct me
							is = "BACKGROUND";
							break;
						case ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY:
							p.imp = 3;
							// no localization? correct me
							is = "NOT ACTIVE";
							break;
						case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
							p.imp = 1;
							// no localization? correct me
							is = "FOREGROUND";
							break;
						case ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE:
							p.imp = 2;
							// no localization? correct me
							is = "SERVICE";
							break;
						case ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE:
							p.imp = 1;
							// no localization? correct me
							is = "VISIBLE";
							break;
						}

						if (newPinfo.containsKey(pids[i]))
							continue; // This task is already in services list

						p.service = false;
						p.clients = 0;
						p.uid = pi.uid;
						p.name = pi.processName;
						p.extra = is;
						p.e = new ArrayList<ExtraInfo>();
						p.mem = mis[i].otherPrivateDirty
								+ mis[i].nativePrivateDirty
								+ mis[i].dalvikSharedDirty;
						try {
							ApplicationInfo ai = pm.getApplicationInfo(
									pi.processName, 0);
							p.icon = pm.getApplicationIcon(ai.packageName);
							p.label = pm.getApplicationLabel(ai).toString();
						} catch (PackageManager.NameNotFoundException e) {
							p.icon = null;
							p.label = "";
						}
						for (int j = 0; j < pi.pkgList.length; j++) {
							ExtraInfo e = new ExtraInfo();
							e.name = pi.pkgList[j];
							try {
								ApplicationInfo ai = pm.getApplicationInfo(
										e.name, 0);
								e.icon = pm.getApplicationIcon(e.name);
								e.label = pm.getApplicationLabel(ai).toString();
							} catch (PackageManager.NameNotFoundException ex) {
								e.icon = null;
								e.label = "";
							}
							p.e.add(e);
						}
						newPinfo.put(pids[i], p);
						newTask.add(pids[i]);
					}
				}

				/*
				 * Merge new pinfo list with the old one
				 */
				for (Iterator<Integer> it = pinfo.keySet().iterator(); it
						.hasNext();) {
					Integer k = it.next();
					if (!newPinfo.containsKey(k))
						it.remove();
				}

				for (Integer k : pinfo.keySet()) {
					PInfo n = newPinfo.get(k);
					pinfo.get(k).mem = n.mem;
				}
				for (Integer k : newPinfo.keySet())
					if (!pinfo.containsKey(k))
						pinfo.put(k, newPinfo.get(k));

				/*
				 * Update CPU usage ----------------
				 */
				getCPU();
				for (Integer k : pinfo.keySet())
					pinfo.get(k).curr = getCPU(k);

				if (cpuTotal_prev != 0 && cpuIdle_prev != 0) {
					// CPU total
					long deltaTotal = cpuTotal - cpuTotal_prev;
					long deltaIdle = cpuIdle - cpuIdle_prev;
					CPUUsage = (deltaTotal != 0) ? (100 * (deltaTotal - deltaIdle))
							/ deltaTotal
							: 0;

					// Per process
					for (Integer k : pinfo.keySet()) {
						PInfo p = pinfo.get(k);
						if (p.prev != 0)
							p.usage = (int) ((deltaTotal != 0) ? (100 * (p.curr - p.prev))
									/ deltaTotal
									: 0);
						pinfo.put(k, p);
					}
				}

				// Update memory usage
				ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
				am.getMemoryInfo(memoryInfo);
				memUsage = memoryInfo.availMem;

				// Save current values as previous
				for (Integer k : pinfo.keySet())
					pinfo.get(k).prev = pinfo.get(k).curr;
				cpuTotal_prev = cpuTotal;
				cpuIdle_prev = cpuIdle;

				return "OK"; // no localization? - correct me
			}

			@Override
			protected void onPostExecute(String result) {
				taskPids = newTask;
				servPids = newServ;
				sortLists();
                EinkScreen.PrepareController(null, false);
				adapter_t.notifyDataSetChanged();
				adapter_s.notifyDataSetChanged();

				// Set titles
				// "Total <b>" + memTotal/1024 + "</b>M / Free: <b>" +
				// memUsage/1048576 + "</b>M"
				title1.setText(
						Html.fromHtml(getResources().getString(
								R.string.jv_taskman_total)
								+ " <b>"
								+ memTotal
								/ 1024
								+ "</b>M / "
								+ getResources().getString(
										R.string.jv_taskman_free)
								+ " <b>"
								+ memUsage / 1048576
                                + "</b>M"),
						TextView.BufferType.SPANNABLE);
				title2.setText(Html.fromHtml("<b>" + CPUUsage + "</b>%"),
						TextView.BufferType.SPANNABLE);
				// "Tasks (" + taskPids.size() + ")"
				tasks_title.setText(getResources().getString(
						R.string.jv_taskman_tasks)
						+ " (" + taskPids.size() + ")");
				// "Services (" + servPids.size() + ")"
				serv_title.setText(getResources().getString(
						R.string.jv_taskman_services)
						+ " (" + servPids.size() + ")");

				/*
				 * Next CPUUpdate_proc call
				 */
				if (CPUUpdate_active)
					CPUUpdate_h.postDelayed(CPUUpdate, CPUUpdate_i);
			}
		};
	}

	private void getCPU() {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream("/proc/stat")), 1000);
			String load = reader.readLine();
			reader.close();

			String[] toks = load.split("\\s+");

			cpuIdle = Long.parseLong(toks[4]) + Long.parseLong(toks[5]);
			cpuTotal = Long.parseLong(toks[1]) + Long.parseLong(toks[2])
					+ Long.parseLong(toks[3]) + cpuIdle
					+ Long.parseLong(toks[6]) + Long.parseLong(toks[7])
					+ Long.parseLong(toks[8]);
		} catch (IOException ex) {
			//emply
		}
	}

	private long getCPU(int pid) {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream("/proc/" + pid + "/stat")), 1000);
			String load = reader.readLine();
			reader.close();

			String[] toks = load.split("\\s+");
			return Long.parseLong(toks[13]) + Long.parseLong(toks[14]);
		} catch (IOException ex) {
			return 0;
		}
	}

	private void showTaskDetails(Integer pid) {
		final Dialog dialog = new Dialog(this);
		final PInfo p = pinfo.get(pid);
		final String localName = p.name;
		final List<ExtraInfo> itemsArray = p.e;

		class TDAdapter extends ArrayAdapter<Integer> {

			TDAdapter(Context context, int resource) {
				super(context, resource);
			}

			@Override
			public int getCount() {
				return itemsArray.size();
			}

			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View v = convertView;
				if (v == null) {
					LayoutInflater vi = (LayoutInflater) getApplicationContext()
							.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					v = vi.inflate(R.layout.results_item, null);
				}

				ExtraInfo e = itemsArray.get(position);
				TextView tv1 = (TextView) v.findViewById(R.id.res_fname);
				TextView tv2 = (TextView) v.findViewById(R.id.res_dname);
				ImageView iv = (ImageView) v.findViewById(R.id.res_icon);

				String label = (e.label.equals("")) ? "SYSTEM" : e.label; // no
																			// localization
																			// ?
																			// correct
																			// me
				SpannableString s = new SpannableString(label);
				s.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), 0);
				tv1.setText(s);
				tv2.setText(e.name);
				if (e.icon == null)
					iv.setImageDrawable(getResources().getDrawable(
							android.R.drawable.sym_def_app_icon));
				else
					iv.setImageDrawable(e.icon);

				return v;
			}
		}

		dialog.setContentView(R.layout.task_details);
		// "Tasks details"
		dialog.setTitle(getResources().getString(
				R.string.jv_taskman_task_details));
		((TextView) dialog.findViewById(R.id.tm1_pid)).setText(pid + " / "
				+ p.uid);
		((TextView) dialog.findViewById(R.id.tm1_label)).setText(p.label);
		((TextView) dialog.findViewById(R.id.tm1_name)).setText(p.name);
		((TextView) dialog.findViewById(R.id.tm1_extra)).setText(p.extra);
		(dialog.findViewById(R.id.tm1_ok)).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						dialog.dismiss();
					}
				});
		Button killBtn = (Button) dialog.findViewById(R.id.tm1_kill);
		killBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				try {
					ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
					ApplicationInfo ai = pm.getApplicationInfo(localName, 0);
					am.restartPackage(ai.packageName);
				} catch (PackageManager.NameNotFoundException e) {
					//emply
				}

				dialog.dismiss();
			}
		});
		boolean killEnable = true;
		boolean doNotKillEmpty = getResources().getBoolean(
				R.bool.do_not_kill_empty_labels);
		if (doNotKillEmpty && p.label.equals(""))
			killEnable = false;
		if (killEnable)
			for (int i = 0; i < doNotKillLabels.length; i++)
				if (p.label.startsWith(doNotKillLabels[i])) {
					killEnable = false;
					break;
				}
		if (killEnable)
			for (int i = 0; i < doNotKillNames.length; i++)
				if (p.name.startsWith(doNotKillNames[i])) {
					killEnable = false;
					break;
				}
		killBtn.setEnabled(killEnable);

		ListView lv = (ListView) dialog.findViewById(R.id.tm1_lv);
		TDAdapter adapter = new TDAdapter(this, R.layout.results_item);
		lv.setAdapter(adapter);

		WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
		params.width = WindowManager.LayoutParams.FILL_PARENT;
		params.height = WindowManager.LayoutParams.FILL_PARENT;
		dialog.getWindow().setAttributes(params);

		dialog.show();
	}

	private void showServiceDetails(Integer pid) {
		final Dialog dialog = new Dialog(this);
		final PInfo p = pinfo.get(pid);
		final String localName = p.name;

		dialog.setContentView(R.layout.service_details);
		// "Service details"
		dialog.setTitle(getResources().getString(
				R.string.jv_taskman_service_details));
		((TextView) dialog.findViewById(R.id.tm2_pid)).setText(pid + " / "
				+ p.uid);
		((TextView) dialog.findViewById(R.id.tm2_label)).setText(p.label);
		((TextView) dialog.findViewById(R.id.tm2_name)).setText(p.name);
		((TextView) dialog.findViewById(R.id.tm2_extra)).setText(p.extra);
		((TextView) dialog.findViewById(R.id.tm2_clients)).setText(Integer
				.toString(p.clients));
		((TextView) dialog.findViewById(R.id.tm2_clientp))
				.setText(p.clientPackage);
		((TextView) dialog.findViewById(R.id.tm2_clientl))
				.setText(p.clientLabel);
		((TextView) dialog.findViewById(R.id.tm2_clientd)).setText(p.descr);
		((Button) dialog.findViewById(R.id.tm2_ok))
				.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						dialog.dismiss();
					}
				});
		Button killBtn = (Button) dialog.findViewById(R.id.tm2_kill);
		killBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				try {
					ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
					ApplicationInfo ai = pm.getApplicationInfo(localName, 0);
					am.restartPackage(ai.packageName);
				} catch (PackageManager.NameNotFoundException e) {
				}
				dialog.dismiss();
			}
		});
		boolean killEnable = true;
		boolean doNotKillEmpty = getResources().getBoolean(
				R.bool.do_not_kill_empty_labels);
		if (doNotKillEmpty && p.label.equals(""))
			killEnable = false;
		if (killEnable)
			for (int i = 0; i < doNotKillLabels.length; i++)
				if (p.label.startsWith(doNotKillLabels[i])) {
					killEnable = false;
					break;
				}
		if (killEnable)
			for (int i = 0; i < doNotKillNames.length; i++)
				if (p.name.startsWith(doNotKillNames[i])) {
					killEnable = false;
					break;
				}
		killBtn.setEnabled(killEnable);

		WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
		params.width = WindowManager.LayoutParams.FILL_PARENT;
		params.height = WindowManager.LayoutParams.FILL_PARENT;
		dialog.getWindow().setAttributes(params);

		dialog.show();
	}

	static class ViewHolder {
		EditText tv1;
		EditText tv2;
		EditText tv3;
		ImageView iv;
	}

	class TAdapter extends ArrayAdapter<Integer> implements OnClickListener {
		Context cntx;

		TAdapter(Context context, int resource) {
			super(context, resource);
			cntx = context;
		}

		public void onClick(View v) {
			final int pos = v.getId();
			showTaskDetails(taskPids.get(pos));
		}

		@Override
		public int getCount() {
			return taskPids.size();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getApplicationContext()
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.taskmanager_item, null);
				holder = new ViewHolder();
				holder.tv1 = (EditText) v.findViewById(R.id.tm_label);
				holder.tv2 = (EditText) v.findViewById(R.id.tm_fullname);
				holder.tv3 = (EditText) v.findViewById(R.id.tm_extra);
				holder.iv = (ImageView) v.findViewById(R.id.tm_icon);

				holder.iv.setOnClickListener(this);
				holder.tv1.setOnClickListener(this);
				holder.tv2.setOnClickListener(this);
				holder.tv3.setOnClickListener(this);

				v.setTag(holder);
			} else
				holder = (ViewHolder) v.getTag();
			TextView tv1 = holder.tv1;
			tv1.setId(position);
			TextView tv2 = holder.tv2;
			tv2.setId(position);
			TextView tv3 = holder.tv3;
			tv3.setId(position);
			ImageView iv = holder.iv;
			iv.setId(position);

			Integer item = taskPids.get(position);
			PInfo pi = pinfo.get(item);
			if (item != null) {
				String label = (pi.label.equals("")) ? "SYSTEM" : pi.label; // no
																			// localization?
																			// //
																			// correct
																			// me
				if (pi.e.size() > 1)
					label = label + " (+" + (pi.e.size() - 1) + ")";
				switch (pi.imp) {
				case 1:
					SpannableString s = new SpannableString(label);
					s.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(),
							0);
					tv1.setBackgroundColor(getResources().getColor(
							R.color.foreground_task_bg));
					tv1.setTextColor(getResources().getColor(
							R.color.foreground_task_fg));
					tv1.setText(s);
					break;
				case 2:
					tv1.setBackgroundColor(getResources().getColor(
							R.color.background_task_bg));
					tv1.setTextColor(getResources().getColor(
							R.color.backgorund_task_fg));
					tv1.setText(label);
					break;
				case 3:
					tv1.setBackgroundColor(getResources().getColor(
							R.color.nonactive_task_bg));
					tv1.setTextColor(getResources().getColor(
							R.color.nonactive_task_fg));
					tv1.setText(label);
					break;
				}
				tv2.setText(pi.name);
				tv3.setText(
						Html.fromHtml(getResources().getString(
								R.string.jv_taskman_cpu)
								+ " <b>"
								+ pi.usage
								+ "</b>%, "
								+ getResources().getString(
										R.string.jv_taskman_mem)
								+ " <b>"
								+ pi.mem + "</b>K"),
						TextView.BufferType.SPANNABLE);
				if (pi.icon == null)
					iv.setImageDrawable(getResources().getDrawable(
							android.R.drawable.sym_def_app_icon));
				else
					iv.setImageDrawable(pi.icon);
			}

			return v;
		}
	}

	class SAdapter extends ArrayAdapter<Integer> implements OnClickListener {
		SAdapter(Context context, int resource) {
			super(context, resource);
		}

		public void onClick(View v) {
			final int pos = v.getId();
			showServiceDetails(servPids.get(pos));
		}

		@Override
		public int getCount() {
			return servPids.size();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getApplicationContext()
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.taskmanager_item, null);
				holder = new ViewHolder();
				holder.tv1 = (EditText) v.findViewById(R.id.tm_label);
				holder.tv2 = (EditText) v.findViewById(R.id.tm_fullname);
				holder.tv3 = (EditText) v.findViewById(R.id.tm_extra);
				holder.iv = (ImageView) v.findViewById(R.id.tm_icon);
				v.setTag(holder);
			} else
				holder = (ViewHolder) v.getTag();
			holder.iv.setOnClickListener(this);
			holder.tv1.setOnClickListener(this);
			holder.tv2.setOnClickListener(this);
			holder.tv3.setOnClickListener(this);

			TextView tv1 = holder.tv1;
			tv1.setId(position);
			TextView tv2 = holder.tv2;
			tv2.setId(position);
			TextView tv3 = holder.tv3;
			tv3.setId(position);
			ImageView iv = holder.iv;
			iv.setId(position);

			Integer item = servPids.get(position);
			PInfo pi = pinfo.get(item);
			if (item != null) {
				String label = (pi.label.equals("")) ? "SYSTEM" : pi.label; // no
																			// localization?
																			// //
																			// correct
																			// me
				tv1.setText(pi.name);
				tv3.setText(
						Html.fromHtml(pi.extra
								+ ", "
								+ getResources().getString(
										R.string.jv_taskman_cpu)
								+ " <b>"
								+ pi.usage
								+ "%</b>, "
								+ getResources().getString(
										R.string.jv_taskman_mem) + " <b>"
								+ pi.mem + "K</b>"),
						TextView.BufferType.SPANNABLE);
				switch (pi.imp) {
				case 1:
					SpannableString s = new SpannableString(label);
					s.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(),0);
					tv1.setBackgroundColor(getResources().getColor(R.color.foreground_task_bg));
					tv1.setTextColor(getResources().getColor(R.color.foreground_task_fg));
					tv1.setText(s);
					break;
				case 2:
					tv1.setBackgroundColor(getResources().getColor(R.color.background_task_bg));
					tv1.setTextColor(getResources().getColor(R.color.backgorund_task_fg));
					tv1.setText(label);
					break;
				case 3:
					tv1.setBackgroundColor(getResources().getColor(R.color.nonactive_task_bg));
					tv1.setTextColor(getResources().getColor(R.color.nonactive_task_fg));
					tv1.setText(label);
					break;
				}
				tv2.setText(pi.name);
				tv3.setText(
						Html.fromHtml(getResources().getString(
								R.string.jv_taskman_cpu)
								+ " <b>"
								+ pi.usage
								+ "</b>%, "
								+ getResources().getString(
										R.string.jv_taskman_mem)
								+ " <b>"
								+ pi.mem + "</b>K"),
						TextView.BufferType.SPANNABLE);
				if (pi.icon == null)
					iv.setImageDrawable(getResources().getDrawable(android.R.drawable.sym_def_app_icon));
				else
					iv.setImageDrawable(pi.icon);
			}
			return v;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        EinkScreen.setEinkController(prefs);

		app = ((ReLaunchApp) getApplicationContext());
        if(app == null ) {
            finish();
        }
        app.setFullScreenIfNecessary(this);
		pm = getPackageManager();
		setContentView(R.layout.taskmanager_layout);

		doNotKillLabels = getResources().getStringArray(
				R.array.do_not_kill_labels);
		doNotKillNames = getResources().getStringArray(
				R.array.do_not_kill_names);

		sortCpu = getResources().getInteger(R.integer.SORT_CPU);
		sortSize = getResources().getInteger(R.integer.SORT_SIZE);
		sortAbc = getResources().getInteger(R.integer.SORT_ABC);
		sortMethod = prefs.getInt("sortMethod", sortCpu);
		lv_t = (ListView) findViewById(R.id.tasks_lv);
		adapter_t = new TAdapter(this, R.layout.taskmanager_item);
		lv_t.setAdapter(adapter_t);

		lv_s = (ListView) findViewById(R.id.services_lv);
		adapter_s = new SAdapter(this, R.layout.taskmanager_item);
		lv_s.setAdapter(adapter_s);

		if (prefs.getBoolean("customScroll", app.customScrollDef)) {
			int scrollW;
			try {
				scrollW = Integer
						.parseInt(prefs.getString("scrollWidth", "25"));
			} catch (NumberFormatException e) {
				scrollW = 25;
			}

			if (addSView_t) {
				LinearLayout ll = (LinearLayout) findViewById(R.id.tasks_lv_layout);
				final SView sv = new SView(getBaseContext());
				LinearLayout.LayoutParams pars = new LinearLayout.LayoutParams(
						scrollW, ViewGroup.LayoutParams.FILL_PARENT, 1f);
				sv.setLayoutParams(pars);
				ll.addView(sv);
				lv_t.setOnScrollListener(new AbsListView.OnScrollListener() {
					public void onScroll(AbsListView view,
							int firstVisibleItem, int visibleItemCount,
							int totalItemCount) {
						sv.total = totalItemCount;
						sv.count = visibleItemCount;
						sv.first = firstVisibleItem;
                        EinkScreen.PrepareController(null, false);
                        sv.invalidate();
					}

					public void onScrollStateChanged(AbsListView view,
							int scrollState) {
					}
				});
				addSView_t = false;
			}

			if (addSView_s) {
				LinearLayout ll = (LinearLayout) findViewById(R.id.services_lv_layout);
				final SView sv = new SView(getBaseContext());
				LinearLayout.LayoutParams pars = new LinearLayout.LayoutParams(
						scrollW, ViewGroup.LayoutParams.FILL_PARENT, 1f);
				sv.setLayoutParams(pars);
				ll.addView(sv);
				lv_s.setOnScrollListener(new AbsListView.OnScrollListener() {
					public void onScroll(AbsListView view,
							int firstVisibleItem, int visibleItemCount,
							int totalItemCount) {
						sv.total = totalItemCount;
						sv.count = visibleItemCount;
						sv.first = firstVisibleItem;
                        if(N2DeviceInfo.EINK_ONYX){
                            EinkScreen.PrepareController(sv, false);
                        }else{
                            EinkScreen.PrepareController(null, false);
                            sv.invalidate();
                        }
					}

					public void onScrollStateChanged(AbsListView view,
							int scrollState) {
					}
				});
				addSView_s = false;
			}
		} else {
			lv_t.setOnScrollListener(new AbsListView.OnScrollListener() {
				public void onScroll(AbsListView view, int firstVisibleItem,
						int visibleItemCount, int totalItemCount) {
                    EinkScreen.PrepareController(null, false);
				}

				public void onScrollStateChanged(AbsListView view,
						int scrollState) {
				}
			});
			lv_s.setOnScrollListener(new AbsListView.OnScrollListener() {
				public void onScroll(AbsListView view, int firstVisibleItem,
						int visibleItemCount, int totalItemCount) {
                    EinkScreen.PrepareController(null, false);
				}

				public void onScrollStateChanged(AbsListView view,
						int scrollState) {
				}
			});
		}

		// Get total memory
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream("/proc/meminfo")), 1000);
			String load = reader.readLine();
			reader.close();

			String[] toks = load.split("\\s+");
			memTotal = Integer.parseInt(toks[1]);
		} catch (IOException ex) {
			memTotal = 0;
		}

		title1 = (TextView) findViewById(R.id.title1_txt);
		title2 = (TextView) findViewById(R.id.title2_txt);
		tasks_title = (TextView) findViewById(R.id.tm_tasks_title);
		serv_title = (TextView) findViewById(R.id.tm_services_title);
		((ImageButton) findViewById(R.id.tm_back))
				.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						finish();
					}
				});
		sortSizeBtn = (Button) findViewById(R.id.sort_size);
		sortSizeBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setSorting(sortSize);
			}
		});
		sortCpuBtn = (Button) findViewById(R.id.sort_cpu);
		sortCpuBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setSorting(sortCpu);
			}
		});
		sortAbcBtn = (Button) findViewById(R.id.sort_abc);
		sortAbcBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setSorting(sortAbc);
			}
		});
		setSorting(sortMethod);
		startCPUUpdate();
		ScreenOrientation.set(this, prefs);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopCPUUpdate();
	}

	@Override
	protected void onPause() {
		super.onPause();
		stopCPUUpdate();
	}


	@Override
	protected void onResume() {
		super.onResume();
        EinkScreen.setEinkController(prefs);
		startCPUUpdate();
		app.generalOnResume(TAG);
	}

	@Override
	protected void onStart() {
		super.onStart();
        EinkScreen.setEinkController(prefs);
		startCPUUpdate();
	}

	@Override
	protected void onStop() {
		super.onStop();
		stopCPUUpdate();
	}

}