package edu.cmu.tetradapp.app.hpc.task;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import org.apache.http.client.ClientProtocolException;

import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.cmu.tetradapp.editor.GeneralAlgorithmEditor;
import edu.cmu.tetradapp.util.DesktopController;
import edu.pitt.dbmi.ccd.rest.client.dto.algo.JobInfo;
import edu.pitt.dbmi.ccd.rest.client.dto.algo.ResultFile;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLog;

/**
 * 
 * Jan 10, 2017 11:37:53 AM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcJobsScheduledTask extends TimerTask {

	public HpcJobsScheduledTask() {
	}

	// Pooling job status from HPC nodes
	@Override
	public void run() {
		TetradDesktop desktop = (TetradDesktop) DesktopController.getInstance();
		if (desktop == null)
			return;

		final HpcAccountManager hpcAccountManager = desktop.getHpcAccountManager();
		final HpcJobManager hpcJobManager = desktop.getHpcJobManager();

		System.out.println("HpcJobsScheduledTask: " + new Date(System.currentTimeMillis()));

		// Load active jobs: Status (0 = Submitted; 1 = Running; 2 = Kill
		// Request)
		Map<HpcAccount, Set<HpcJobInfo>> submittedHpcJobInfos = hpcJobManager.getSubmittedHpcJobInfoMap();
		if (submittedHpcJobInfos.size() == 0) {
			System.out.println("Submitted job pool is empty!");
		} else {
			System.out.println("Submitted job pool has " + submittedHpcJobInfos.keySet().size() + " hpcAccount"
					+ (submittedHpcJobInfos.keySet().size() > 1 ? "s" : ""));
		}

		for (HpcAccount hpcAccount : submittedHpcJobInfos.keySet()) {

			System.out.println("HpcJobsScheduledTask: " + hpcAccount.getConnectionName());

			Set<HpcJobInfo> hpcJobInfos = submittedHpcJobInfos.get(hpcAccount);
			// Pid-HpcJobInfo map
			Map<Long, HpcJobInfo> hpcJobInfoMap = new HashMap<>();
			for (HpcJobInfo hpcJobInfo : hpcJobInfos) {
				if (hpcJobInfo.getPid() != null) {
					long pid = hpcJobInfo.getPid().longValue();
					hpcJobInfoMap.put(pid, hpcJobInfo);

					System.out.println("id: " + hpcJobInfo.getId() + " : " + hpcJobInfo.getAlgorithmName() + ": pid: "
							+ pid + " : " + hpcJobInfo.getResultFileName());

				} else {

					System.out.println("id: " + hpcJobInfo.getId() + " : " + hpcJobInfo.getAlgorithmName()
							+ ": no pid! : " + hpcJobInfo.getResultFileName());

					hpcJobInfos.remove(hpcJobInfo);
				}
			}

			// Finished job map
			HashMap<Long, HpcJobInfo> finishedJobMap = new HashMap<>();
			for (HpcJobInfo job : hpcJobInfos) {
				finishedJobMap.put(job.getPid(), job);
			}

			try {
				List<JobInfo> jobInfos = hpcJobManager.getRemoteActiveJobs(hpcAccountManager, hpcAccount);

				for (JobInfo jobInfo : jobInfos) {
					System.out.println("Remote pid: " + jobInfo.getId() + " : " + jobInfo.getAlgorithmName() + " : "
							+ jobInfo.getResultFileName());

					long pid = jobInfo.getId();

					if (finishedJobMap.containsKey(pid)) {
						finishedJobMap.remove(pid);
					}

					int remoteStatus = jobInfo.getStatus();
					String recentStatusText = (remoteStatus == 0 ? "Submitted"
							: (remoteStatus == 1 ? "Running" : "Kill Request"));
					HpcJobInfo hpcJobInfo = hpcJobInfoMap.get(pid);// Local job
					// map
					HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);
					if (hpcJobInfo != null) {
						int status = hpcJobInfo.getStatus();
						if (status != remoteStatus) {
							// Update status
							hpcJobInfo.setStatus(remoteStatus);

							hpcJobManager.updateHpcJobInfo(hpcJobInfo);
							hpcJobLog.setLastUpdatedTime(new Date(System.currentTimeMillis()));

							String log = "Job status changed to " + recentStatusText;
							System.out.println(hpcJobInfo.getAlgorithmName() + " : id : " + hpcJobInfo.getId()
									+ " : pid : " + pid);
							System.out.println(log);

							hpcJobManager.logHpcJobLogDetail(hpcJobLog, remoteStatus, log);
						}
					}
				}

				// Download finished jobs' results
				if (finishedJobMap.size() > 0) {
					Set<ResultFile> resultFiles = hpcJobManager.listRemoteAlgorithmResultFiles(hpcAccountManager,
							hpcAccount);

					Set<String> resultFileNames = new HashSet<>();
					for (ResultFile resultFile : resultFiles) {
						resultFileNames.add(resultFile.getName());
						// System.out.println(hpcAccount.getConnectionName()
						// + " Result : " + resultFile.getName());
					}

					for (HpcJobInfo hpcJobInfo : finishedJobMap.values()) {// Job
						// is
						// done
						// or
						// killed or
						// time-out
						HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);
						String recentStatusText = "Job finished";
						int recentStatus = 3; // Finished
						if (hpcJobInfo.getStatus() == 2) {
							recentStatusText = "Job killed";
							recentStatus = 4; // Killed
						}
						hpcJobInfo.setStatus(recentStatus);
						hpcJobManager.updateHpcJobInfo(hpcJobInfo);

						// System.out.println("hpcJobInfo: id: "
						// + hpcJobInfo.getId() + " : "
						// + hpcJobInfo.getStatus());

						hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, recentStatusText);

						System.out.println(hpcJobInfo.getAlgorithmName() + " : id : " + hpcJobInfo.getId() + " : "
								+ recentStatusText);

						GeneralAlgorithmEditor editor = hpcJobManager.getGeneralAlgorithmEditor(hpcJobInfo);
						if (editor != null) {
							System.out.println("GeneralAlgorithmEditor is not null");

							String resultJsonFileName = hpcJobInfo.getResultJsonFileName();
							String errorResultFileName = hpcJobInfo.getErrorResultFileName();

							if (resultFileNames.contains(resultJsonFileName)) {
								recentStatus = 5; // Result Downloaded

								String json = downloadAlgorithmResultFile(hpcAccountManager, hpcJobManager, hpcAccount,
										resultJsonFileName, editor);

								if (!json.toLowerCase().contains("not found")) {
									editor.setAlgorithmResult(json);
								}

								String log = "Result downloaded";
								hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, log);

								System.out.println(
										hpcJobInfo.getAlgorithmName() + " : id : " + hpcJobInfo.getId() + " : " + log);

							} else if (resultFileNames.contains(errorResultFileName)) {
								recentStatus = 6; // Error Result Downloaded

								String error = downloadAlgorithmResultFile(hpcAccountManager, hpcJobManager, hpcAccount,
										errorResultFileName, editor);

								if (!error.toLowerCase().contains("not found")) {
									editor.setAlgorithmErrorResult(error);
								}

								String log = "Error Result downloaded";
								hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, log);

								System.out.println(
										hpcJobInfo.getAlgorithmName() + " : id : " + hpcJobInfo.getId() + " : " + log);

							} else {

								// Try again
								Thread.sleep(5000);

								String json = downloadAlgorithmResultFile(hpcAccountManager, hpcJobManager, hpcAccount,
										resultJsonFileName, editor);

								if (!json.toLowerCase().contains("not found")) {
									editor.setAlgorithmResult(json);

									recentStatus = 5; // Result Downloaded

									String log = "Result downloaded";
									hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, log);

									System.out.println(hpcJobInfo.getAlgorithmName() + " : id : " + hpcJobInfo.getId()
											+ " : " + log);
								} else {
									String error = downloadAlgorithmResultFile(hpcAccountManager, hpcJobManager,
											hpcAccount, errorResultFileName, editor);

									if (!error.toLowerCase().contains("not found")) {
										editor.setAlgorithmErrorResult(error);

										recentStatus = 6; // Error Result
										// Downloaded

										String log = "Error Result downloaded";
										hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, log);

										System.out.println(hpcJobInfo.getAlgorithmName() + " : id : "
												+ hpcJobInfo.getId() + " : " + log);
									} else {
										recentStatus = 7; // Result Not Found

										String log = resultJsonFileName + " not found";
										hpcJobManager.logHpcJobLogDetail(hpcJobLog, recentStatus, log);

										System.out.println(hpcJobInfo.getAlgorithmName() + " : id : "
												+ hpcJobInfo.getId() + " : " + log);
									}

								}

							}

						}
						hpcJobManager.removeFinishedHpcJob(hpcJobInfo);
					}
				} else {
					System.out.println("No finished job yet.");
				}

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	private String downloadAlgorithmResultFile(final HpcAccountManager hpcAccountManager,
			final HpcJobManager hpcJobManager, final HpcAccount hpcAccount, final String resultFileName,
			final GeneralAlgorithmEditor editor)
			throws ClientProtocolException, URISyntaxException, IOException, Exception {
		int trial = 10;
		String txt = hpcJobManager.downloadAlgorithmResultFile(hpcAccountManager, hpcAccount, resultFileName);
		while (trial != 0 && txt.toLowerCase().contains("not found")) {
			Thread.sleep(5000);
			txt = hpcJobManager.downloadAlgorithmResultFile(hpcAccountManager, hpcAccount, resultFileName);
			trial--;
		}

		return txt;
	}

}
