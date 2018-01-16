package com.apicloud.uzneteaseavchat;

import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.Observer;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.SDKOptions;
import com.netease.nimlib.sdk.auth.AuthService;
import com.netease.nimlib.sdk.auth.AuthServiceObserver;
import com.netease.nimlib.sdk.auth.ClientType;
import com.netease.nimlib.sdk.auth.LoginInfo;
import com.netease.nimlib.sdk.auth.constant.LoginSyncStatus;
import com.netease.nimlib.sdk.avchat.AVChatCallback;
import com.netease.nimlib.sdk.avchat.AVChatManager;
import com.netease.nimlib.sdk.avchat.AVChatStateObserver;
import com.netease.nimlib.sdk.avchat.constant.AVChatControlCommand;
import com.netease.nimlib.sdk.avchat.constant.AVChatEventType;
import com.netease.nimlib.sdk.avchat.constant.AVChatType;
import com.netease.nimlib.sdk.avchat.constant.AVChatVideoScalingType;
import com.netease.nimlib.sdk.avchat.model.AVChatAudioFrame;
import com.netease.nimlib.sdk.avchat.model.AVChatCalleeAckEvent;
import com.netease.nimlib.sdk.avchat.model.AVChatCommonEvent;
import com.netease.nimlib.sdk.avchat.model.AVChatControlEvent;
import com.netease.nimlib.sdk.avchat.model.AVChatData;
import com.netease.nimlib.sdk.avchat.model.AVChatNotifyOption;
import com.netease.nimlib.sdk.avchat.model.AVChatOnlineAckEvent;
import com.netease.nimlib.sdk.avchat.model.AVChatParameters;
import com.netease.nimlib.sdk.avchat.model.AVChatVideoFrame;
import com.netease.nimlib.sdk.avchat.model.AVChatVideoRender;
import com.uzmap.pkg.uzcore.UZCoreUtil;
import com.uzmap.pkg.uzcore.UZWebView;
import com.uzmap.pkg.uzcore.uzmodule.UZModule;
import com.uzmap.pkg.uzcore.uzmodule.UZModuleContext;

public class UzNetEaseAVChat extends UZModule implements AVChatStateObserver {

	private boolean mIsInit = false;
	private AVChatVideoRender mLocalRender;
	private AVChatVideoRender mRemoteRender;

	public UzNetEaseAVChat(UZWebView webView) {
		super(webView);
	}

	public void jsmethod_init(UZModuleContext moduleContext) {
		NIMClient.init(mContext, null, getOptions());
		mIsInit = true;
		registerLoginSyncDataStatus(true);
		initCallBack(moduleContext);
	}

	public void registerLoginSyncDataStatus(boolean register) {
		NIMClient.getService(AuthServiceObserver.class)
				.observeLoginSyncDataStatus(loginSyncStatusObserver, register);
	}

	Observer<LoginSyncStatus> loginSyncStatusObserver = new Observer<LoginSyncStatus>() {
		@Override
		public void onEvent(LoginSyncStatus status) {
			if (status == LoginSyncStatus.BEGIN_SYNC) {
			} else if (status == LoginSyncStatus.SYNC_COMPLETED) {
			}
		}
	};

	public boolean inMainProcess(Context context) {
		String packageName = context.getPackageName();
		String processName = getProcessName(context);
		return packageName.equals(processName);
	}

	public final String getProcessName(Context context) {
		String processName = null;
		ActivityManager am = ((ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE));

		while (true) {
			for (ActivityManager.RunningAppProcessInfo info : am
					.getRunningAppProcesses()) {
				if (info.pid == android.os.Process.myPid()) {
					processName = info.processName;
					break;
				}
			}
			if (!TextUtils.isEmpty(processName)) {
				return processName;
			}
			try {
				Thread.sleep(100L);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
	}

	public void jsmethod_setParams(UZModuleContext moduleContext) {
		AVChatParameters avChatParameters = new AVChatParameters();
		boolean autoCallProximity = moduleContext.optBoolean(
				"autoCallProximity", true);
		boolean defaultFrontCamera = moduleContext.optBoolean(
				"defaultFrontCamera", true);
		int videoQuality = moduleContext.optInt("videoQuality", 480);
		avChatParameters.setBoolean(AVChatParameters.KEY_AUDIO_CALL_PROXIMITY,
				autoCallProximity);
		avChatParameters.setBoolean(
				AVChatParameters.KEY_VIDEO_DEFAULT_FRONT_CAMERA,
				defaultFrontCamera);
		avChatParameters.setInteger(AVChatParameters.KEY_VIDEO_QUALITY,
				videoQuality);
		AVChatManager.getInstance().setParameters(avChatParameters);
	}

	@SuppressWarnings("unchecked")
	public void jsmethod_login(final UZModuleContext moduleContext) {
		String account = moduleContext.optString("account");
		String token = moduleContext.optString("token");
		LoginInfo info = new LoginInfo(account, token);
		RequestCallback<LoginInfo> callback = new RequestCallback<LoginInfo>() {

			@Override
			public void onException(Throwable throwable) {
				loginCallBack(moduleContext, false, -1, throwable.getMessage(),
						null);
			}

			@Override
			public void onFailed(int code) {
				loginCallBack(moduleContext, false, code, null, null);
			}

			@Override
			public void onSuccess(LoginInfo loginInfo) {
				loginCallBack(moduleContext, true, 0, null, loginInfo);
			}
		};
		NIMClient.getService(AuthService.class).login(info)
				.setCallback(callback);
	}

	public void jsmethod_logout(final UZModuleContext moduleContext) {
		if (mIsInit) {
			NIMClient.getService(AuthService.class).logout();
		}
	}

	private LayoutParams mLocalLayoutParams;

	public void jsmethod_setLocalVideoRender(UZModuleContext moduleContext) {
		if (mLocalRender == null) {
			mLocalRender = new AVChatVideoRender(mContext);
		}
		mLocalLayoutParams = createLocalLayoutParams(moduleContext);
	}

	private boolean mIsLocalVideoAdded = false;

	public void jsmethod_startLocalVideoRender(UZModuleContext moduleContext) {
		if (mLocalRender != null) {
			if (mIsLocalVideoAdded) {
				removeViewFromCurWindow(mLocalRender);
			}
			AVChatManager.getInstance().setupLocalVideoRender(mLocalRender,
					false, AVChatVideoScalingType.SCALE_ASPECT_BALANCED);
			String fixedOn = moduleContext.optString("fixedOn");
			boolean fixed = moduleContext.optBoolean("fixed", true);
			insertViewToCurWindow(mLocalRender, mLocalLayoutParams, fixedOn,
					fixed);
			mIsLocalVideoAdded = true;
			boolean isZFront = moduleContext.optBoolean("zFront");
			mLocalRender.setZOrderMediaOverlay(isZFront);
		}
	}

	private LayoutParams mRemoteLayoutParams;

	public void jsmethod_setRemoteVideoRender(UZModuleContext moduleContext) {
		if (mRemoteRender == null) {
			mRemoteRender = new AVChatVideoRender(mContext);
		}
		mRemoteLayoutParams = createRemoteLayoutParams(moduleContext);
		System.out.println("setRemoteVideoRender");
	}

	private boolean mIsRemoteVideoAdded = false;

	public void jsmethod_startRemoteVideoRender(UZModuleContext moduleContext) {
		if (mRemoteRender != null) {
			if (mIsRemoteVideoAdded) {
				removeViewFromCurWindow(mRemoteRender);
			}
			String account = moduleContext.optString("account");
			AVChatManager.getInstance().setupRemoteVideoRender(account,
					mRemoteRender, false,
					AVChatVideoScalingType.SCALE_ASPECT_BALANCED);
			String fixedOn = moduleContext.optString("fixedOn");
			boolean fixed = moduleContext.optBoolean("fixed", true);
			insertViewToCurWindow(mRemoteRender, mRemoteLayoutParams, fixedOn,
					fixed);
			mIsRemoteVideoAdded = true;
			boolean isZFront = moduleContext.optBoolean("zFront");
			mRemoteRender.setZOrderMediaOverlay(isZFront);
			System.out.println("startRemoteVideoRender");
		}
	}

	public void jsmethod_closeLocalVideo(UZModuleContext moduleContext) {
		if (mLocalRender != null) {
			mIsLocalVideoAdded = false;
			removeViewFromCurWindow(mLocalRender);
		}
	}

	public void jsmethod_hideLocalVideo(UZModuleContext moduleContext) {
		if (mLocalRender != null) {
			mLocalRender.setVisibility(View.GONE);
		}
	}

	public void jsmethod_showLocalVideo(UZModuleContext moduleContext) {
		if (mLocalRender != null) {
			mLocalRender.setVisibility(View.VISIBLE);
		}
	}

	public void jsmethod_closeRemoteVideo(UZModuleContext moduleContext) {
		if (mRemoteRender != null) {
			mIsRemoteVideoAdded = false;
			removeViewFromCurWindow(mRemoteRender);
		}
	}

	public void jsmethod_hideRemoteVideo(UZModuleContext moduleContext) {
		if (mRemoteRender != null) {
			mRemoteRender.setVisibility(View.GONE);
		}
	}

	public void jsmethod_showRemoteVideo(UZModuleContext moduleContext) {
		if (mRemoteRender != null) {
			mRemoteRender.setVisibility(View.VISIBLE);
		}
	}

	public void jsmethod_setRtc(UZModuleContext moduleContext) {
		boolean enable = moduleContext.optBoolean("enable", true);
		if (enable) {
			boolean status = AVChatManager.getInstance().enableRtc();
			statusCallBack(moduleContext, status);
		} else {
			boolean status = AVChatManager.getInstance().disableRtc();
			statusCallBack(moduleContext, status);
		}
	}

	public void jsmethod_setVideo(UZModuleContext moduleContext) {
		boolean enable = moduleContext.optBoolean("enable", true);
		if (enable) {
			boolean status = AVChatManager.getInstance().enableVideo();
			statusCallBack(moduleContext, status);
		} else {
			boolean status = AVChatManager.getInstance().disableVideo();
			statusCallBack(moduleContext, status);
		}
	}

	public void jsmethod_setPreview(UZModuleContext moduleContext) {
		boolean enable = moduleContext.optBoolean("enable", true);
		if (enable) {
			boolean status = AVChatManager.getInstance().startVideoPreview();
			statusCallBack(moduleContext, status);
		} else {
			boolean status = AVChatManager.getInstance().stopVideoPreview();
			statusCallBack(moduleContext, status);
		}
	}

	public void jsmethod_switchCamera(UZModuleContext moduleContext) {
		AVChatManager.getInstance().switchCamera();
	}

	public void jsmethod_setSpeaker(UZModuleContext moduleContext) {
		AVChatManager.getInstance().setSpeaker(
				AVChatManager.getInstance().speakerEnabled());
	}

	public void jsmethod_call(final UZModuleContext moduleContext) {
		String account = moduleContext.optString("account");
		final AVChatType callTypeEnum = moduleContext
				.optString("type", "audio").equals("audio") ? AVChatType.AUDIO
				: AVChatType.VIDEO;
		AVChatManager.getInstance().call2(account, callTypeEnum,
				new AVChatNotifyOption(), new AVChatCallback<AVChatData>() {

					@Override
					public void onSuccess(AVChatData avChatData) {
						callCallBack(moduleContext, avChatData, true, 0);
					}

					@Override
					public void onFailed(int code) {
						callCallBack(moduleContext, null, false, code);
					}

					@Override
					public void onException(Throwable throwable) {
						callCallBack(moduleContext, null, false, -1);
					}
				});

	}

	public void jsmethod_addComingCallingListener(
			final UZModuleContext moduleContext) {
		boolean enable = moduleContext.optBoolean("enable", true);
		AVChatManager.getInstance().observeIncomingCall(
				new Observer<AVChatData>() {

					@Override
					public void onEvent(AVChatData avChatData) {
						comingCallingCallBack(moduleContext, avChatData);
					}
				}, enable);
	}

	public void jsmethod_addOnlineAckListener(
			final UZModuleContext moduleContext) {
		boolean enable = moduleContext.optBoolean("enable", true);
		AVChatManager.getInstance().observeOnlineAckNotification(
				new Observer<AVChatOnlineAckEvent>() {

					@Override
					public void onEvent(AVChatOnlineAckEvent ackEvent) {
						onlineAckCallBack(moduleContext, ackEvent);
					}
				}, enable);
	}

	public void jsmethod_addHangUpListener(final UZModuleContext moduleContext) {
		boolean enable = moduleContext.optBoolean("enable", true);
		AVChatManager.getInstance().observeHangUpNotification(
				new Observer<AVChatCommonEvent>() {

					@Override
					public void onEvent(AVChatCommonEvent event) {
						hangUpCallBack(moduleContext, event);
					}
				}, enable);
	}

	public void jsmethod_accept(final UZModuleContext moduleContext) {
		long chatId = moduleContext.optLong("chatId");
		AVChatManager.getInstance().accept2(chatId, new AVChatCallback<Void>() {

			@Override
			public void onSuccess(Void obj) {
				simpleCallBack(moduleContext, true, 0);
			}

			@Override
			public void onFailed(int code) {
				simpleCallBack(moduleContext, false, code);
			}

			@Override
			public void onException(Throwable arg0) {
				simpleCallBack(moduleContext, false, -1);
			}
		});
	}

	public void jsmethod_hangUp(final UZModuleContext moduleContext) {
		long chatId = moduleContext.optLong("chatId");
		AVChatManager.getInstance().hangUp2(chatId, new AVChatCallback<Void>() {

			@Override
			public void onSuccess(Void obj) {
				simpleCallBack(moduleContext, true, 0);
			}

			@Override
			public void onFailed(int code) {
				simpleCallBack(moduleContext, false, code);
			}

			@Override
			public void onException(Throwable arg0) {
				simpleCallBack(moduleContext, false, -1);
			}
		});
	}

	public void jsmethod_calleeAckListener(final UZModuleContext moduleContext) {
		boolean enable = moduleContext.optBoolean("enable", true);
		AVChatManager.getInstance().observeCalleeAckNotification(
				new Observer<AVChatCalleeAckEvent>() {

					@Override
					public void onEvent(
							AVChatCalleeAckEvent avChatCalleeAckEvent) {
						System.out.println();
						calleeAckCallBack(moduleContext, avChatCalleeAckEvent);
					}
				}, enable);
	}

	public void jsmethod_sendControlCommand(final UZModuleContext moduleContext) {
		long chatId = moduleContext.optLong("chatId");
		String command = moduleContext.optString("command");
		byte commandCode = AVChatControlCommand.SWITCH_AUDIO_TO_VIDEO;
		if (command.equals("audio2Video")) {
			commandCode = AVChatControlCommand.SWITCH_AUDIO_TO_VIDEO;
		} else if (command.equals("video2Audio")) {
			commandCode = AVChatControlCommand.SWITCH_VIDEO_TO_AUDIO;
		} else if (command.equals("audio2VideoAgree")) {
			commandCode = AVChatControlCommand.SWITCH_AUDIO_TO_VIDEO_AGREE;
		} else if (command.equals("video2AudioAgree")) {
			commandCode = AVChatControlCommand.NOTIFY_VIDEO_ON;
		} else if (command.equals("audio2VideoReject")) {
			commandCode = AVChatControlCommand.SWITCH_AUDIO_TO_VIDEO_REJECT;
		} else if (command.equals("video2AudioReject")) {
			commandCode = AVChatControlCommand.NOTIFY_VIDEO_OFF;
		}
		AVChatManager.getInstance().sendControlCommand(chatId, commandCode,
				new AVChatCallback<Void>() {

					@Override
					public void onSuccess(Void arg0) {
						simpleCallBack(moduleContext, true, 0);
					}

					@Override
					public void onFailed(int code) {
						simpleCallBack(moduleContext, false, code);
					}

					@Override
					public void onException(Throwable arg0) {
						simpleCallBack(moduleContext, false, -1);
					}
				});
	}

	public void jsmethod_controlListener(final UZModuleContext moduleContext) {
		boolean enable = moduleContext.optBoolean("enable", true);
		AVChatManager.getInstance().observeControlNotification(
				new Observer<AVChatControlEvent>() {

					@Override
					public void onEvent(AVChatControlEvent event) {
						commandCallBack(moduleContext, event);
					}
				}, enable);
	}

	private void commandCallBack(UZModuleContext moduleContext,
			AVChatControlEvent avChatControlEvent) {
		JSONObject ret = new JSONObject();
		try {
			ret.put("account", avChatControlEvent.getAccount());
			ret.put("chatId", avChatControlEvent.getChatId());
			ret.put("chatType",
					avChatControlEvent.getChatType() == AVChatType.AUDIO ? "AUDIO"
							: "VIDEO");
			ret.put("extra", avChatControlEvent.getExtra());
			String event = "audio2Video";
			if (avChatControlEvent.getControlCommand() == AVChatControlCommand.SWITCH_AUDIO_TO_VIDEO) {
				event = "audio2Video";
			} else if (avChatControlEvent.getControlCommand() == AVChatControlCommand.SWITCH_VIDEO_TO_AUDIO) {
				event = "video2Audio";
			} else if (avChatControlEvent.getControlCommand() == AVChatControlCommand.SWITCH_AUDIO_TO_VIDEO_AGREE) {
				event = "audio2VideoAgree";
			} else if (avChatControlEvent.getControlCommand() == AVChatControlCommand.SWITCH_AUDIO_TO_VIDEO_REJECT) {
				event = "audio2VideoReject";
			} else if (avChatControlEvent.getControlCommand() == AVChatControlCommand.NOTIFY_VIDEO_ON) {
				event = "video2AudioAgree";
			} else if (avChatControlEvent.getControlCommand() == AVChatControlCommand.NOTIFY_VIDEO_OFF) {
				event = "video2AudioReject";
			}
			ret.put("event", event);
			moduleContext.success(ret, false);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void calleeAckCallBack(UZModuleContext moduleContext,
			AVChatCalleeAckEvent avChatCalleeAckEvent) {
		JSONObject ret = new JSONObject();
		try {
			ret.put("status", true);
			ret.put("account", avChatCalleeAckEvent.getAccount());
			ret.put("chatId", avChatCalleeAckEvent.getChatId());
			ret.put("chatType",
					avChatCalleeAckEvent.getChatType() == AVChatType.AUDIO ? "AUDIO"
							: "VIDEO");
			ret.put("extra", avChatCalleeAckEvent.getExtra());
			String event = "busy";
			if (avChatCalleeAckEvent.getEvent() == AVChatEventType.CALLEE_ACK_AGREE) {
				event = "agree";
			} else if (avChatCalleeAckEvent.getEvent() == AVChatEventType.CALLEE_ACK_BUSY) {
				event = "busy";
			} else if (avChatCalleeAckEvent.getEvent() == AVChatEventType.CALLEE_ACK_REJECT) {
				event = "reject";
			}
			ret.put("event", event);
			moduleContext.success(ret, false);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void hangUpCallBack(UZModuleContext moduleContext,
			AVChatCommonEvent event) {
		JSONObject ret = new JSONObject();
		try {
			ret.put("status", true);
			ret.put("account", event.getAccount());
			ret.put("chatId", event.getChatId());
			ret.put("chatType",
					event.getChatType() == AVChatType.AUDIO ? "AUDIO" : "VIDEO");
			ret.put("extra", event.getExtra());
			moduleContext.success(ret, false);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void onlineAckCallBack(UZModuleContext moduleContext,
			AVChatOnlineAckEvent ackEvent) {
		JSONObject ret = new JSONObject();
		try {
			ret.put("status", true);
			ret.put("account", ackEvent.getAccount());
			ret.put("chatId", ackEvent.getChatId());
			ret.put("chatType",
					ackEvent.getChatType() == AVChatType.AUDIO ? "AUDIO"
							: "VIDEO");
			String clientType = "Android";
			switch (ackEvent.getClientType()) {
			case ClientType.Android:
				clientType = "Android";
				break;
			case ClientType.iOS:
				clientType = "iOS";
				break;
			case ClientType.Web:
				clientType = "Web";
				break;
			case ClientType.Windows:
				clientType = "Windows";
				break;
			case ClientType.WP:
				clientType = "WP";
				break;
			case ClientType.REST:
				clientType = "REST";
				break;
			case ClientType.UNKNOW:
				clientType = "UNKNOW";
				break;
			}
			ret.put("clientType", clientType);
			ret.put("extra", ackEvent.getExtra());
			moduleContext.success(ret, false);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void comingCallingCallBack(UZModuleContext moduleContext,
			AVChatData avChatData) {
		JSONObject ret = new JSONObject();
		try {
			ret.put("account", avChatData.getAccount());
			ret.put("chatId", avChatData.getChatId());
			ret.put("chatType",
					avChatData.getChatType() == AVChatType.AUDIO ? "AUDIO"
							: "VIDEO");
			ret.put("extra", avChatData.getExtra());
			moduleContext.success(ret, false);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void initCallBack(UZModuleContext moduleContext) {
		JSONObject ret = new JSONObject();
		try {
			ret.put("status", true);
			moduleContext.success(ret, true);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void simpleCallBack(UZModuleContext moduleContext, boolean status,
			int code) {
		JSONObject ret = new JSONObject();
		JSONObject err = new JSONObject();
		try {
			ret.put("status", status);
			if (status) {
				moduleContext.success(ret, true);
			} else {
				err.put("code", code);
				moduleContext.error(ret, err, true);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void callCallBack(UZModuleContext moduleContext,
			AVChatData avChatData, boolean status, int code) {
		JSONObject ret = new JSONObject();
		JSONObject err = new JSONObject();
		try {
			ret.put("status", status);
			if (status) {
				ret.put("account", avChatData.getAccount());
				ret.put("chatId", avChatData.getChatId());
				ret.put("chatType",
						avChatData.getChatType() == AVChatType.AUDIO ? "AUDIO"
								: "VIDEO");
				ret.put("extra", avChatData.getExtra());
				moduleContext.success(ret, true);
			} else {
				err.put("code", code);
				moduleContext.error(ret, err, true);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void loginCallBack(UZModuleContext moduleContext, boolean status,
			int code, String msg, LoginInfo loginInfo) {
		JSONObject ret = new JSONObject();
		JSONObject err = new JSONObject();
		try {
			ret.put("status", status);
			if (status) {
				ret.put("account", loginInfo.getAccount());
				ret.put("token", loginInfo.getToken());
				moduleContext.success(ret, true);
			} else {
				err.put("code", code);
				err.put("msg", msg);
				moduleContext.error(ret, err, true);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void statusCallBack(UZModuleContext moduleContext, boolean status) {
		JSONObject ret = new JSONObject();
		try {
			ret.put("status", status);
			moduleContext.success(ret, true);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private SDKOptions getOptions() {
		SDKOptions options = new SDKOptions();
		options.sdkStorageRootPath = Environment.getExternalStorageDirectory()
				+ "/" + mContext.getPackageName() + "/nim";
		options.databaseEncryptKey = "NETEASE";
		options.sessionReadAck = true;
		return options;
	}

	private LayoutParams createLocalLayoutParams(UZModuleContext moduleContext) {
		JSONObject rect = moduleContext.optJSONObject("rect");
		int x = getWindowWidth(mContext) / 2;
		int y = getWindowHeight(mContext) / 2;
		int w = getWindowWidth(mContext) / 2;
		int h = getWindowHeight(mContext) / 2;
		if (rect != null) {
			x = rect.optInt("x", 0);
			y = rect.optInt("y", 0);
			w = rect.optInt("w", w);
			h = rect.optInt("h", h);
		}
		LayoutParams layoutParams = new LayoutParams(w, h);
		layoutParams.setMargins(x, y, 0, 0);
		return layoutParams;
	}

	private LayoutParams createRemoteLayoutParams(UZModuleContext moduleContext) {
		JSONObject rect = moduleContext.optJSONObject("rect");
		int x = 0;
		int y = 0;
		int w = getWindowWidth(mContext);
		int h = getWindowHeight(mContext);
		if (rect != null) {
			x = rect.optInt("x", 0);
			y = rect.optInt("y", 0);
			w = rect.optInt("w", w);
			h = rect.optInt("h", h);
		}
		LayoutParams layoutParams = new LayoutParams(w, h);
		layoutParams.setMargins(x, y, 0, 0);
		return layoutParams;
	}

	private int getWindowWidth(Activity context) {
		DisplayMetrics metric = new DisplayMetrics();
		context.getWindowManager().getDefaultDisplay().getMetrics(metric);
		int windowWidth = metric.widthPixels;
		return UZCoreUtil.pixToDip(windowWidth);
	}

	private int getWindowHeight(Activity context) {
		DisplayMetrics metric = new DisplayMetrics();
		context.getWindowManager().getDefaultDisplay().getMetrics(metric);
		int heightPixels = metric.heightPixels;
		return UZCoreUtil.pixToDip(heightPixels);
	}

	@Override
	public void onAVRecordingCompletion(String arg0, String arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAudioDeviceChanged(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onAudioFrameFilter(AVChatAudioFrame arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onAudioMixingEvent(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAudioRecordingCompletion(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onCallEstablished() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onConnectionTypeChanged(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onDeviceEvent(int arg0, String arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onDisconnectServer() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFirstVideoFrameAvailable(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFirstVideoFrameRendered(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onJoinedChannel(int arg0, String arg1, String arg2) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLeaveChannel() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLowStorageSpaceWarning(long arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onNetworkQuality(String arg0, int arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProtocolIncompatible(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onReportSpeaker(Map<String, Integer> arg0, int arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTakeSnapshotResult(String arg0, boolean arg1, String arg2) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onUserJoined(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onUserLeave(String arg0, int arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onVideoFpsReported(String arg0, int arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onVideoFrameFilter(AVChatVideoFrame arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onVideoFrameResolutionChanged(String arg0, int arg1, int arg2,
			int arg3) {
		// TODO Auto-generated method stub

	}
}
