package jp.co.sony.csl.dcoes.apis.tools.ccc.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.AbstractStarter;
import jp.co.sony.csl.dcoes.apis.tools.ccc.DealReporting;
import jp.co.sony.csl.dcoes.apis.tools.ccc.PolicyAcquisition;
import jp.co.sony.csl.dcoes.apis.tools.ccc.ScenarioAcquisition;
import jp.co.sony.csl.dcoes.apis.tools.ccc.UnitDataReporting;

/**
 * apis-ccc の親玉 Verticle.
 * pom.xml の maven-shade-plugin の {@literal <Main-Verticle>} で指定してある.
 * 以下の Verticle を起動する.
 * - {@link DealReporting} : 融通情報を外部に通知する Verticle
 * - {@link UnitDataReporting} : ユニットデータを外部に通知する Verticle
 * - {@link ScenarioAcquisition} : 外部から SCENARIO を取得する Verticle
 * - {@link PolicyAcquisition} : 外部から POLICY を取得する Verticle
 * @author OES Project
 */
public class Starter extends AbstractStarter {

	/**
	 * 起動時に {@link AbstractStarter#start(Future)} から呼び出される.
	 */
	@Override protected void doStart(Handler<AsyncResult<Void>> completionHandler) {
		vertx.deployVerticle(new DealReporting(), resDealReporting -> {
			if (resDealReporting.succeeded()) {
				vertx.deployVerticle(new UnitDataReporting(), resUnitDataReporting -> {
					if (resUnitDataReporting.succeeded()) {
						vertx.deployVerticle(new ScenarioAcquisition(), resScenarioAcquisition -> {
							if (resScenarioAcquisition.succeeded()) {
								vertx.deployVerticle(new PolicyAcquisition(), resPolicyAcquisition -> {
									if (resPolicyAcquisition.succeeded()) {
										completionHandler.handle(Future.succeededFuture());
									} else {
										completionHandler.handle(Future.failedFuture(resPolicyAcquisition.cause()));
									}
								});
							} else {
								completionHandler.handle(Future.failedFuture(resScenarioAcquisition.cause()));
							}
						});
					} else {
						completionHandler.handle(Future.failedFuture(resUnitDataReporting.cause()));
					}
				});
			} else {
				completionHandler.handle(Future.failedFuture(resDealReporting.cause()));
			}
		});
	}

}
