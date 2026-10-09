package com.ibkglobal.integrator.common;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.Callable;

import lombok.Data;

public class ExecutorModel {
	
	public static class TaskRunnable implements Runnable {

		@Override
		public void run() {
			try {				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static class TaskCallBackRunnable implements Runnable {

		private CompletionHandler<TaskResultModel, Void> callback = new CompletionHandler<ExecutorModel.TaskResultModel, Void>() {
			@Override
			public void completed(TaskResultModel result, Void attachment) {
				// CallBack Completed
			}

			@Override
			public void failed(Throwable exc, Void attachment) {
				// CallBack Fail
			}
		};
		
		@Override
		public void run() {
			try {
				callback.completed(new TaskResultModel(), null);
			} catch (Exception e) {
				callback.failed(e, null);
			}
		}		
	}
	
	public static class TaskCallable implements Callable<TaskResultModel> {

		@Override
		public TaskResultModel call() throws Exception {
			TaskResultModel result = null; 
			
			try {				
				result = new TaskResultModel();
			} catch (Exception e) {
				result = new TaskResultModel(false, e, e.getMessage());
			}
			
			return result;
		}		
	}
	
	@Data
	public static class TaskResultModel {
		
		private boolean   result;
		private Exception exception;
		private String    error;
		
		public TaskResultModel() {
			this.result    = true;
		}
		
		public TaskResultModel(boolean result, Exception exception, String error) {
			this.result    = result;
			this.exception = exception;
			this.error     = error;
		}
	}
}
