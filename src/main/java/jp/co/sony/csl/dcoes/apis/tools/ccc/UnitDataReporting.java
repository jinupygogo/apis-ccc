package jp.co.sony.csl.dcoes.apis.tools.ccc;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.tools.ccc.impl.kl_cc.KnowledgeLineUnitDataReportingImpl;
import jp.co.sony.csl.dcoes.apis.tools.ccc.impl.mongo_db.MongoDBUnitDataReportingImpl;

/**
 * ユニットデータを外部に通知する Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.tools.ccc.util.Starter} Verticle から起動される.
 * 一定時間ごとに GridMaster からユニットデータを取得し通知する.
 * 実際の通知処理は以下の 2 つ.
 * - {@code CONFIG.unitDataReporting.type} が {@code kl_cc} : {@link KnowledgeLineUnitDataReportingImpl}
 * - {@code CONFIG.unitDataReporting.type} が {@code mongo_db} : {@link MongoDBUnitDataReportingImpl}
 * @author OES Project
 */
public class UnitDataReporting extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(UnitDataReporting.class);

	/**
	 * 通知周期のデフォルト値 [ms].
	 * 値は {@value}
	 */
	private static final Long DEFAULT_UNIT_DATA_REPORTING_PERIOD_MSEC = 30000L;
	/**
	 * 通知方式のデフォルト値.
	 * 値は {@value}
	 */
	private static final JsonObjectUtil.DefaultString DEFAULT_UNIT_DATA_REPORTING_TYPE = new JsonObjectUtil.DefaultString("mongo_db");

	private Impl impl_;
	private boolean enabled_ = false;
	private long unitDataReportingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * 起動時に呼び出される.
	 * CONFIG から設定を取得し初期化する.
	 * - {@code CONFIG.unitDataReporting.enabled}
	 * - {@code CONFIG.unitDataReporting.type}
	 * 実装オブジェクトを用意する.
	 * タイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		enabled_ = VertxConfig.config.getBoolean(Boolean.TRUE, "unitDataReporting", "enabled");
		if (enabled_) {
			if (log.isInfoEnabled()) log.info("unitDataReporting enabled");
			String type = VertxConfig.config.getString(DEFAULT_UNIT_DATA_REPORTING_TYPE, "unitDataReporting", "type");
			try {
				switch (type) {
				case "kl_cc":
					impl_ = new KnowledgeLineUnitDataReportingImpl(vertx);
					break;
				case "mongo_db":
					impl_ = new MongoDBUnitDataReportingImpl(vertx);
					break;
				}
				if (impl_ == null) {
					startFuture.fail("unknown CONFIG.unitDataReporting.type value : " + type);
					return;
				}
			} catch (Exception e) {
				startFuture.fail(e);
				return;
			}
		} else {
			if (log.isInfoEnabled()) log.info("unitDataReporting disabled");
		}

		if (enabled_) unitDataReportingTimerHandler_(0L);
		if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
		startFuture.complete();
	}

	/**
	 * 停止時に呼び出される.
	 * タイマを止めるためのフラグを立てる.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	/**
	 * デフォルト時間でタイマをセットする.
	 */
	private void setUnitDataReportingTimer_() {
		Long delay = VertxConfig.config.getLong(DEFAULT_UNIT_DATA_REPORTING_PERIOD_MSEC, "unitDataReporting", "periodMsec");
		setUnitDataReportingTimer_(delay);
	}
	/**
	 * {@code delay} で指定した時間でタイマをセットする.
	 * @param delay タイマ設定時間 [ms]
	 */
	private void setUnitDataReportingTimer_(long delay) {
		unitDataReportingTimerId_ = vertx.setTimer(delay, this::unitDataReportingTimerHandler_);
	}
	/**
	 * タイマから呼び出される処理.
	 * @param timerId タイマ ID
	 */
	private void unitDataReportingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != unitDataReportingTimerId_) {
			if (log.isWarnEnabled()) log.warn("illegal timerId : " + timerId + ", unitDataReportingTimerId_ : " + unitDataReportingTimerId_);
			return;
		}
		if (log.isInfoEnabled()) log.info("reporting unit data ...");
		vertx.eventBus().<JsonObject>send(ServiceAddress.GridMaster.unitDatas(), null, rep -> {
			if (rep.succeeded()) {
				JsonObject result = rep.result().body();
				if (result != null) {
					if (log.isInfoEnabled()) log.info("size of unit data : " + result.size());
					if (!result.isEmpty()) {
						// 要素が JsonObject であることを保証する
						JsonObject filtered = new JsonObject();
						for (String aKey : result.fieldNames()) {
							Object aVal = result.getValue(aKey);
							if (aVal instanceof JsonObject) {
								filtered.put(aKey, aVal);
							} else {
								if (log.isWarnEnabled()) log.warn("invalid unitData item : " + aVal);
							}
						}
						result = filtered;
						if (log.isInfoEnabled()) log.info("size of filtered unit data : " + result.size());
					}
					if (!result.isEmpty()) {
						// データセット ID を追加する
						long id = System.currentTimeMillis();
						if (log.isDebugEnabled()) log.debug("datasetId : " + id);
						for (String aKey : result.fieldNames()) {
							JsonObject aVal = result.getJsonObject(aKey);
							aVal.put("datasetId", id);
						}
						impl_.report(result, resReport -> {
							if (resReport.succeeded()) {
								// nop
							} else {
								log.error("Communication failed with ServiceCenter ; " + resReport.cause());
							}
							setUnitDataReportingTimer_();
						});
					} else {
						if (log.isWarnEnabled()) log.warn("unit data is empty");
						setUnitDataReportingTimer_();
					}
				} else {
					if (log.isWarnEnabled()) log.warn("unit data is null");
					setUnitDataReportingTimer_();
				}
			} else {
				log.error("Communication failed on EventBus ; " + rep.cause());
				setUnitDataReportingTimer_();
			}
		});
	}

	////

	/**
	 * 通知処理の実装オブジェクトを呼び出すためのインタフェイス.
	 * @author OES Project
	 */
	public interface Impl {
		/**
		 * 融通情報を一つ通知する.
		 * @param unitData ユニットデータ {@link JsonObject}
		 * @param completionHandler the completion handler
		 */
		void report(JsonObject unitData, Handler<AsyncResult<Void>> completionHandler);
	}

}
