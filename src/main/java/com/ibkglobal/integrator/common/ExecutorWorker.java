package com.ibkglobal.integrator.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.ibkglobal.integrator.common.ExecutorModel.TaskRunnable;
import com.ibkglobal.integrator.common.ExecutorModel.TaskCallBackRunnable;
import com.ibkglobal.integrator.common.ExecutorModel.TaskCallable;
import com.ibkglobal.integrator.common.ExecutorModel.TaskResultModel;

@Component
public class ExecutorWorker {
	
	@Bean
	private ExecutorService executorService() {
		ExecutorService executorService = Executors.newCachedThreadPool();		
		return executorService;
	}
	
	@Bean
	private CompletionService<TaskResultModel> completionService() {
		CompletionService<TaskResultModel> completionService = new ExecutorCompletionService<>(executorService());
		return completionService;
	}
	
	/**
	 * 일반 스레드
	 * @param taskRunnable
	 * @throws Exception
	 */
	public void execute(TaskRunnable taskRunnable) throws Exception {		
		executorService().submit(taskRunnable);
	}
	
	/**
	 * CallBack 스레드
	 * @param taskCallBackRunnable
	 * @throws Exception
	 */
	public void execute(TaskCallBackRunnable taskCallBackRunnable) throws Exception {
		executorService().submit(taskCallBackRunnable);
	}
	
	/**
	 * Call 스레드
	 * @param taskCallable
	 * @return
	 * @throws Exception
	 */
	public TaskResultModel execute(TaskCallable taskCallable) throws Exception {
		Future<TaskResultModel> future = executorService().submit(taskCallable);				
		return future.get();
	}
	
	/**
	 * 멀티 스레드(콜)
	 * @param taskCallables
	 * @return
	 * @throws Exception
	 */
	public List<TaskResultModel> executes(List<TaskCallable> taskCallables) throws Exception {
		List<TaskResultModel> result = new ArrayList<>();
		
		List<Future<TaskResultModel>> futures = executorService().invokeAll(taskCallables);		
		for (Future<TaskResultModel> future : futures) {
			result.add(future.get());
		}
		
		return result;
	}
	
	public void shutDown() {
		executorService().shutdown();
	}
}
