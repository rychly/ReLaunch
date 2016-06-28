package com.harasoft.relaunch;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class SearchActivity extends Activity {
	final String TAG = "Search";
	int SEARCH_FILE;
	int SEARCH_LASTDIR;
	int SEARCH_FPATH;
	int SEARCH_PATH;

	SharedPreferences prefs;
	Spinner searchAs;
	Spinner searchIn;
	CheckBox searchCase;
	CheckBox searchKnown;
	CheckBox searchSort;
	EditText searchRoot;
	EditText searchTxt;
	InputMethodManager imm;
	ReLaunchApp app;

	ProgressDialog pd;
	boolean stop_search = false;

	// Search parameters and result
	List<String[]> searchResults;
	int filesCount;

	private void resetSearch() {
		searchResults = new ArrayList<String[]>();
		filesCount = 0;
		stop_search = false;

		pd = new ProgressDialog(this);
		pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		// "Search in progress"
		pd.setMessage(getResources().getString(R.string.jv_search_in_progress));
		pd.setCancelable(true);
		// "Cancel"
		pd.setButton(ProgressDialog.BUTTON_NEGATIVE,
				getResources().getString(R.string.app_cancel),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						stop_search = true;
					}
				});
		pd.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface dialog) {
				stop_search = true;
			}
		});
		pd.show();
	}

	public AsyncTask<Boolean, Integer, String> createAsyncTask() {
		return new AsyncTask<Boolean, Integer, String>() {
			Boolean case_sens;
			Boolean known_only;
			Boolean regexp;
			String pattern;
			Boolean search_sort;
			int search_mode;
			int searchReport;
			int searchSize;

			private void addToResults(String dname, String fname, boolean is_dir) {
				String[] n = new String[2];

				if (is_dir) {
					n[0] = dname; // fullPath;
					n[1] = app.DIR_TAG;
					boolean found = false;
					for (String r[] : searchResults) {
						if (r[0].equals(n[0]) && r[1].equals(n[1])) {
							found = true;
							break;
						}
					}
					if (!found)
						searchResults.add(n);
				} else {
					n[0] = dname;
					n[1] = fname;
					searchResults.add(n);
				}
			}

			private void compareAdd(String dname, String fname, String fullPath, boolean is_dir) {
				String item; // Item for comparison
				if (search_mode == SEARCH_FILE) {
					if (known_only && app.readerName(fname).equals("Nope"))
						return;
					item = fname;
				} else if (search_mode == SEARCH_PATH) {
					item = dname;
					fname = app.DIR_TAG;
					is_dir = true;
				} else if (search_mode == SEARCH_LASTDIR) {
					String i[] = dname.split("/");
					if (i.length > 0)
						item = i[i.length - 1];
					else
						item = dname;
					fname = app.DIR_TAG;
					is_dir = true;
				} else if (search_mode == SEARCH_FPATH) {
					if (known_only && app.readerName(fname).equals("Nope"))
						return;
					item = fullPath;
				} else
					item = fullPath; // Should not be here!!!

				if (regexp) {
					// Regular expression
					if (item.matches(pattern))
						addToResults(dname, fname,  is_dir);
				} else {
					// String
					if (case_sens) {
						if (item.contains(pattern))
							addToResults(dname, fname, is_dir);
					} else {
						if (item.toLowerCase().contains(pattern.toLowerCase()))
							addToResults(dname, fname, is_dir);
					}
				}
			}

			private void addEntries(String roots) {
				String[] r = roots.split(",");
				for (String root : r) {
					File dir = new File(root);
					if (!dir.isDirectory())
						continue;
					File[] allEntries = dir.listFiles();
					if (allEntries == null)
						continue;
					for (File entry : allEntries) {
						filesCount++;
						String entryFullName = root
								+ (root.equals("/") ? "" : "/")
								+ entry.getName();
						if ((filesCount % searchReport) == 0)
							publishProgress(filesCount);

						if (stop_search)
							break;
						if (searchResults.size() >= searchSize)
							break;

						if (entry.isDirectory()) {
							if (search_mode == SEARCH_PATH)
								compareAdd(root, entry.getName(),
										entryFullName, true);
							addEntries(entryFullName);
						} else
							compareAdd(root, entry.getName(), entryFullName,
									false);
					}
				}
			}

			@Override
			protected String doInBackground(Boolean... params) {
				Boolean all = params[0];

				searchResults = new ArrayList<String[]>();
				filesCount = 0;
				try {
					searchReport = Integer.parseInt(prefs.getString(
							"searchReport", "100"));
					searchSize = Integer.parseInt(prefs.getString("searchSize",
							"5000"));
				} catch (NumberFormatException e) {
					searchReport = 100;
					searchSize = 5000;
				}
				stop_search = false;

				// Save all general search settings
				String root = String.valueOf(searchRoot.getText());
				search_sort = searchSort.isChecked();
				SharedPreferences.Editor editor = prefs.edit();
				editor.putBoolean("searchSort", search_sort);
				editor.putString("searchRoot", root);
				editor.commit();

				if (all) {
					case_sens = false;
					known_only = true;
					regexp = true;
					pattern = ".*";
					addEntries(root);
				} else {
					case_sens = searchCase.isChecked();
					known_only = searchKnown.isChecked();
					regexp = searchAs.getSelectedItemPosition() == 1;
					pattern = String.valueOf(searchTxt.getText());
					search_mode = searchIn.getSelectedItemPosition();

					// Save all specific search settings
					editor.putBoolean("searchCase", case_sens);
					editor.putBoolean("searchKnown", known_only);
					editor.putInt("searchAs", searchAs.getSelectedItemPosition());
					editor.putInt("searchIn", searchIn.getSelectedItemPosition());
					editor.putString("searchRoot", root);
					editor.putString("searchPrev", pattern);
					editor.commit();

					// Search
					addEntries(root);
				}

				return "OK"; // no localization? correct me
			}

			@Override
			protected void onPostExecute(String result) {
				super.onPostExecute(result);
				pd.dismiss();
				stop_search = false;
				if (search_sort) {
					final class SRComparator implements java.util.Comparator<String[]> {
						public int compare(String[] o1, String[] o2) {
							/*int rc = o1[1].compareTo(o2[1]);
							// next commented, reason - sometimes don't work
							// with russian on Nook 8()
							// if (rc == 0)
							// return o1[0].compareTo(o2[0]);
							// else
							return rc;*/
                            return o1[1].compareTo(o2[1]);
						}
					}
					SRComparator o1c = new SRComparator();
					Collections.sort(searchResults, o1c);
				}
				app.setList("searchResults", searchResults);
				Intent intent = new Intent(SearchActivity.this,ResultsActivity.class);
				intent.putExtra("list", "searchResults");
				// "Search results"
				intent.putExtra("title",getResources().getString(R.string.jv_search_results));
				intent.putExtra("rereadOnStart", false);
				intent.putExtra("total", filesCount);
				startActivity(intent);
			}

			@Override
			protected void onProgressUpdate(Integer... values) {
				super.onProgressUpdate(values);
				// "Files (found / total searched) " + searchResults.size() +
				// "/"
				pd.setMessage(getResources().getString(
						R.string.jv_search_files_total)
						+ " " + searchResults.size() + "/" + filesCount);
			}
		};
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = ((ReLaunchApp) getApplicationContext());
        if(app == null ) {
            finish();
        }
        app.setFullScreenIfNecessary(this);
		setContentView(R.layout.search);

		SEARCH_FILE = getResources().getInteger(R.integer.SEARCH_FILE);
		SEARCH_LASTDIR = getResources().getInteger(R.integer.SEARCH_LASTDIR);
		SEARCH_FPATH = getResources().getInteger(R.integer.SEARCH_FPATH);
		SEARCH_PATH = getResources().getInteger(R.integer.SEARCH_PATH);

		stop_search = false;

		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		searchCase = (CheckBox) findViewById(R.id.search_case);
		searchKnown = (CheckBox) findViewById(R.id.search_books_only);
		searchSort = (CheckBox) findViewById(R.id.search_sort);
		searchAs = (Spinner) findViewById(R.id.search_as);
		searchIn = (Spinner) findViewById(R.id.search_in);
		searchRoot = (EditText) findViewById(R.id.search_root);
		searchTxt = (EditText) findViewById(R.id.search_txt);
		searchTxt.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    resetSearch();
                    createAsyncTask().execute(false);
                    return true;
                }
                return false;
            }
        });

		// Set main search button
		(findViewById(R.id.search_btn)).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						resetSearch();
						createAsyncTask().execute(false);
					}
				});

		// Search all button
		(findViewById(R.id.search_all)).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						resetSearch();
						createAsyncTask().execute(true);
					}
				});

		// Back button - work as cancel
		( findViewById(R.id.back_btn)).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						finish();
					}
				});

		ScreenOrientation.set(this, prefs);
	}

	@Override
	protected void onStart() {
		super.onStart();

		// Set case sensitive checkbox
		searchCase.setChecked(prefs.getBoolean("searchCase", false));

		// Set search known extensions only checkbox
		searchKnown.setChecked(prefs.getBoolean("searchKnown", true));

		// Set search sort checkbox
		searchSort.setChecked(prefs.getBoolean("searchSort", true));

		// Set search as spinner
		ArrayAdapter<CharSequence> adapter1 = ArrayAdapter.createFromResource(
				this, R.array.search_as_values,
				android.R.layout.simple_spinner_item);
		adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		searchAs.setAdapter(adapter1);
		searchAs.setSelection(prefs.getInt("searchAs", 0), false);

		// Set search in spinner
		ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(
				this, R.array.search_in_values,
				android.R.layout.simple_spinner_item);
		adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		searchIn.setAdapter(adapter2);
		searchIn.setSelection(prefs.getInt("searchIn", 0), false);

		// set search root
		searchRoot.setText(prefs.getString("searchRoot", ReLaunch.currentRoot));
        if (String.valueOf(searchRoot.getText()).equals("")){
            searchRoot.setText(ReLaunch.currentRoot);
        }

		// Set search text
		searchTxt.setText(prefs.getString("searchPrev", ""));

	}

	@Override
	protected void onResume() {
		super.onResume();
		searchAs.setSelection(prefs.getInt("searchAs", 0), false);
		searchIn.setSelection(prefs.getInt("searchIn", 0), false);
		app.generalOnResume(TAG);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.searchmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.about:
			app.About(this);
			return true;
		case R.id.setting:
			Intent intent = new Intent(SearchActivity.this, PrefsActivity.class);
			startActivity(intent);
			return true;
		default:
			return true;
		}
	}
}
