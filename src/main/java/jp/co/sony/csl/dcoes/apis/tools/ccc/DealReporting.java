package jp.co.sony.csl.dcoes.apis.tools.ccc;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.tools.ccc.impl.http_post.HttpPostDealReportingImpl;
import jp.co.sony.csl.dcoes.apis.tools.ccc.impl.mongo_db.MongoDBDealReportingImpl;

/**
 * 融通情報を外部に通知する Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.tools.ccc.util.Starter} Verticle から起動される.
 * 一定時間ごとに Mediator から融通情報を取得し通知する.
 * 実際の通知処理は以下の 2 つ.
 * - {@code CONFIG.dealReporting.type} が {@code http_post} : {@link HttpPostDealReportingImpl}
 * - {@code CONFIG.dealReporting.type} が {@code mongo_db} : {@link MongoDBDealReportingImpl}
 * @author OES Project
 */
public class DealReporting extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DealReporting.class);

	/**
	 * 通知周期のデフォルト値 [ms].
	 * 値は {@value}
	 */
	private static final Long DEFAULT_DEAL_REPORTING_PERIOD_MSEC = 30000L;
	/**
	 * 通知方式のデフォルト値.
	 * 値は {@value}
	 */
	private static final JsonObjectUtil.DefaultString DEFAULT_DEAL_REPORTING_TYPE = new JsonObjectUtil.DefaultString("mongo_db");

	private Impl impl_;
	private boolean enabled_ = false;
	private long dealReportingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * 起動時に呼び出される.
	 * CONFIG から設定を取得し初期化する.
	 * - {@code CONFIG.dealReporting.enabled}
	 * - {@code CONFIG.dealReporting.type}
	 * 実装オブジェクトを用意する.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * タイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		enabled_ = VertxConfig.config.getBoolean(Boolean.TRUE, "dealReporting", "enabled");
		if (enabled_) {
			if (log.isInfoEnabled()) log.info("dealReporting enabled");
			String type = VertxConfig.config.getString(DEFAULT_DEAL_REPORTING_TYPE, "dealReporting", "type");
			try {
				switch (type) {
				case "http_post":
					impl_ = new HttpPostDealReportingImpl(vertx);
					break;
				case "mongo_db":
					impl_ = new MongoDBDealReportingImpl(vertx);
					break;
				}
				if (impl_ == null) {
					startFuture.fail("unknown CONFIG.dealReporting.type value : " + type);
					return;
				}
			} catch (Exception e) {
				startFuture.fail(e);
				return;
			}
		} else {
			if (log.isInfoEnabled()) log.info("dealReporting disabled");
		}

		startDealReportingService_(resDealReporting -> {
			if (resDealReporting.succeeded()) {
				if (enabled_) dealReportingTimerHandler_(0L);
				if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
				startFuture.complete();
			} else {
				startFuture.fail(resDealReporting.cause());
			}
		});
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
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#dealLogging()}
	 * 範囲 : グローバル
	 * 処理 : Service Center に対し融通情報を通知する.
	 * メッセージボディ : 融通情報 [{@link JsonObject}]
	 * メッセージヘッダ : なし
	 * レスポンス : 通知機能が有効なら {@code "ok"}.
	 * 　　　　　   通知機能が無効なら {@code "N/A"}.
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDealReportingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealLogging(), req -> {
			if (enabled_) {
				JsonObject deal = req.body();
				if (deal != null && Deal.isSaveworthy(deal)) {
					// コミュニティ ID とクラスタ ID を追加する
					deal.put("communityId", VertxConfig.communityId()).put("clusterId", VertxConfig.clusterId());
					// 通知時間 ( 実時間の UNIX 時間 ) を追加する
					long now = System.currentTimeMillis() / 1000;
					if (log.isDebugEnabled()) log.debug("reportTime : " + now);
					deal.put("reportTime", now);
					impl_.report(deal, resReport -> {
						if (resReport.succeeded()) {
							req.reply("ok");
						} else {
							log.error("Communication failed with ServiceCenter ; " + resReport.cause());
							req.fail(-1, resReport.cause().getMessage());
						}
					});
				} else {
					req.fail(-1, "deal is null");
				}
			} else {
				req.reply("N/A");
			}
		}).completionHandler(completionHandler);
	}

	////

	/**
	 * デフォルト時間でタイマをセットする.
	 */
	private void setDealReportingTimer_() {
		Long delay = VertxConfig.config.getLong(DEFAULT_DEAL_REPORTING_PERIOD_MSEC, "dealReporting", "periodMsec");
		setDealReportingTimer_(delay);
	}
	/**
	 * {@code delay} で指定した時間でタイマをセットする.
	 * @param delay タイマ設定時間 [ms]
	 */
	private void setDealReportingTimer_(long delay) {
		dealReportingTimerId_ = vertx.setTimer(delay, this::dealReportingTimerHandler_);
	}
	/**
	 * タイマから呼び出される処理.
	 * @param timerId タイマ ID
	 */
	private void dealReportingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != dealReportingTimerId_) {
			if (log.isWarnEnabled()) log.warn("illegal timerId : " + timerId + ", dealReportingTimerId_ : " + dealReportingTimerId_);
			return;
		}
		if (log.isInfoEnabled()) log.info("reporting deals ...");
		vertx.eventBus().<JsonArray>send(ServiceAddress.Mediator.deals(), null, rep -> {
			if (rep.succeeded()) {
				JsonArray result = rep.result().body();
				if (result != null) {
					if (log.isInfoEnabled()) log.info("size of result : " + result.size());
					if (!result.isEmpty()) {
						// 記録する必要のない DEAL 情報を捨てる
						JsonArray filtered = new JsonArray();
						for (Object aDeal : result) {
							if (aDeal instanceof JsonObject && Deal.isSaveworthy((JsonObject) aDeal)) {
								filtered.add(aDeal);
							}
						}
						result = filtered;
						if (log.isInfoEnabled()) log.info("size of filtered result : " + result.size());
					}
					if (!result.isEmpty()) {
						long now = System.currentTimeMillis() / 1000;
						if (log.isDebugEnabled()) log.debug("reportTime : " + now);
						for (Object aDeal : result) {
							// コミュニティ ID とクラスタ ID を追加する
							((JsonObject) aDeal).put("communityId", VertxConfig.communityId()).put("clusterId", VertxConfig.clusterId());
							// 通知時間 ( 実時間の UNIX 時間 ) を追加する
							((JsonObject) aDeal).put("reportTime", now);
						}
						impl_.report(result, resReport -> {
							if (resReport.succeeded()) {
								// nop
							} else {
								log.error("Communication failed with ServiceCenter ; " + resReport.cause());
							}
							setDealReportingTimer_();
						});
					} else {
						setDealReportingTimer_();
					}
				} else {
					if (log.isWarnEnabled()) log.warn("result is null");
					setDealReportingTimer_();
				}
			} else {
				log.error("Communication failed on EventBus ; " + rep.cause());
				setDealReportingTimer_();
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
		 * @param deal 融通情報 {@link JsonObject}
		 * @param completionHandler the completion handler
		 */
		void report(JsonObject deal, Handler<AsyncResult<Void>> completionHandler);
		/**
		 * 複数の融通情報を一度に通知する.
		 * @param deals 融通情報 {@link JsonObject} の配列 {@link JsonArray}
		 * @param completionHandler the completion handler
		 */
		void report(JsonArray deals, Handler<AsyncResult<Void>> completionHandler);
	}

}
