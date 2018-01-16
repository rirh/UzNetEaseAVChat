package com.apicloud.uzneteaseavchat;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.SDKOptions;
import com.uzmap.pkg.uzcore.uzmodule.AppInfo;
import com.uzmap.pkg.uzcore.uzmodule.ApplicationDelegate;

/**
 * 继承自ApplicationDelegate的类，APICloud引擎在应用初始化之初就会将该类初始化一次（即new一个出来），并保持引用，
 * 在应用整个运行期间，会将生命周期事件通过该对象通知出来。<br>
 * 该类需要在module.json中配置，其中name字段可以任意配置，因为该字段将被忽略，请参考module.json中EasDelegate的配置
 */
public class NetEaseAVChatEasDelegate extends ApplicationDelegate {

	/**
	 * 继承自ApplicationDelegate的类，APICloud引擎在应用初始化之初就会将该类初始化一次（即new一个出来），并保持引用，
	 * 在应用整个运行期间，会将生命周期事件通过该对象通知出来。<br>
	 * 该类需要在module.json中配置，其中name字段可以任意配置，因为该字段将被忽略，请参考module.json中EasDelegate的配置
	 */
	public NetEaseAVChatEasDelegate() {
		//应用运行期间，该对象只会初始化一个出来
	}

	@Override
	public boolean supportMulti() {
		return true;
	}

	@Override
	public void onApplicationCreate(Context context, AppInfo info) {
		NIMClient.init(context, null, getOptions(context));
	}
	
	private SDKOptions getOptions(Context context) {
		SDKOptions options = new SDKOptions();
		options.sdkStorageRootPath = Environment.getExternalStorageDirectory()
				+ "/" + context.getPackageName() + "/nim";
		options.databaseEncryptKey = "NETEASE";
		options.sessionReadAck = true;
		return options;
	}

	@Override
	public void onActivityResume(Activity activity, AppInfo info) {
		//APP从后台回到前台时，APICloud引擎将通过该函数回调事件
		//TODO 请在这个函数中实现你需要的逻辑，无则忽略
	}

	@Override
	public void onActivityPause(Activity activity, AppInfo info) {
		//APP从前台退到后台时，APICloud引擎将通过该函数回调事件
		//TODO 请在这个函数中实现你需要的逻辑，无则忽略
	}

	@Override
	public void onActivityFinish(Activity activity, AppInfo info) {
		//APP即将结束运行时，APICloud引擎将通过该函数回调事件
		//TODO 请在这个函数中实现你需要的逻辑，无则忽略
	}

}
