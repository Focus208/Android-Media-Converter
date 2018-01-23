package com.github.khangnt.mcp.ui.job_manager

import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.khangnt.mcp.R
import com.github.khangnt.mcp.SingletonInstances
import com.github.khangnt.mcp.annotation.JobStatus.*
import com.github.khangnt.mcp.job.jobComparator
import com.github.khangnt.mcp.ui.BaseFragment
import com.github.khangnt.mcp.ui.common.AdapterModel
import com.github.khangnt.mcp.ui.common.HeaderModel
import com.github.khangnt.mcp.worker.ConverterService
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_job_manager.*
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by Khang NT on 1/5/18.
 * Email: khang.neon.1997@gmail.com
 */

class JobManagerFragment : BaseFragment() {

    private val jobManager = SingletonInstances.getJobManager()
    private val adapter = JobAdapter(jobManager.getLiveLogObservable()).apply { setHasStableIds(true) }
    private val runningHeaderModel = RunningHeaderModel("Running")
    private val preparingHeaderModel = HeaderModel("Preparing")
    private val readyHeaderModel = HeaderModel("Ready")
    private val pendingHeaderModel = HeaderModel("Pending")
    private val finishedHeaderModel = HeaderModel("Finished")

    private var loadDataDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // launch service
        context!!.startService(Intent(context!!, ConverterService::class.java))
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_job_manager, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerViewGroup.recyclerView!!.adapter = adapter
        recyclerViewGroup.recyclerView!!.layoutManager = LinearLayoutManager(context)
        recyclerViewGroup.onRetry = { loadData() }

        loadData()
    }

    private fun loadData() {
        recyclerViewGroup.loading()
        loadDataDisposable = jobManager.getJob(RUNNING, PREPARING, READY, PENDING, COMPLETED, FAILED)
                .throttleLast(400, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.computation())
                .doOnNext { Collections.sort(it, jobComparator) }
                .map { originalList ->
                    val listModels = mutableListOf<AdapterModel>()
                    var addedFinishedHeader = false
                    var previousStatus: Int? = null
                    originalList.forEach { item ->
                        if (item.status != previousStatus) {
                            previousStatus = item.status
                            if (item.status == RUNNING) {
                                listModels.add(runningHeaderModel)
                            } else if (item.status == PREPARING) {
                                listModels.add(preparingHeaderModel)
                            } else if (item.status == READY) {
                                listModels.add(readyHeaderModel)
                            } else if (item.status == PENDING) {
                                listModels.add(pendingHeaderModel)
                            } else if (!addedFinishedHeader) {
                                addedFinishedHeader = true
                                listModels.add(finishedHeaderModel)
                            }
                        }
                        listModels.add(JobModel(item))
                    }
                    return@map listModels
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ list ->
                    adapter.setData(list)
                    if (list.isEmpty()) {
                        recyclerViewGroup.empty()
                    } else {
                        recyclerViewGroup.successWithData()
                    }
                }, { error ->
                    Timber.e(error, "Load job list failed")
                    adapter.setData(emptyList())
                    recyclerViewGroup.error(error.message)
                })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerViewGroup.recyclerView?.adapter = null
        loadDataDisposable?.dispose() // stop load data
    }

}