/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Changes for TIBCO Project SnappyData data platform.
 *
 * Portions Copyright (c) 2017-2020 TIBCO Software Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package org.apache.spark.deploy.master.ui

import java.io.DataOutputStream
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import java.util.Date

import scala.collection.mutable.HashMap

import org.mockito.Mockito.{mock, times, verify, when}
import org.scalatest.BeforeAndAfterAll

import org.apache.spark.{SecurityManager, SparkConf, SparkFunSuite}
import org.apache.spark.deploy.DeployMessages.{KillDriverResponse, RequestKillDriver}
import org.apache.spark.deploy.DeployTestUtils._
import org.apache.spark.deploy.master._
import org.apache.spark.rpc.{RpcEndpointRef, RpcEnv}


class MasterWebUISuite extends SparkFunSuite with BeforeAndAfterAll {

  val conf = new SparkConf
  val securityMgr = new SecurityManager(conf)
  val rpcEnv = mock(classOf[RpcEnv])
  val master = mock(classOf[Master])
  val masterEndpointRef = mock(classOf[RpcEndpointRef])
  when(master.securityMgr).thenReturn(securityMgr)
  when(master.conf).thenReturn(conf)
  when(master.rpcEnv).thenReturn(rpcEnv)
  when(master.self).thenReturn(masterEndpointRef)
  val masterWebUI = new MasterWebUI(master, 0)

  override def beforeAll() {
    super.beforeAll()
    masterWebUI.bind()
  }

  override def afterAll() {
    masterWebUI.stop()
    super.afterAll()
  }

  test("kill application") {
    val appDesc = createAppDesc()
    // use new start date so it isn't filtered by UI
    val activeApp = new ApplicationInfo(
      new Date().getTime, "app-0", appDesc, new Date(), null, Int.MaxValue)

    when(master.idToApp).thenReturn(HashMap[String, ApplicationInfo]((activeApp.id, activeApp)))

    val url = s"http://localhost:${masterWebUI.boundPort}/app/kill/"
    val body = convPostDataToString(Map(("id", activeApp.id), ("terminate", "true")))
    val conn = sendHttpRequest(url, "POST", body)
    conn.getResponseCode

    // Verify the master was called to remove the active app
    verify(master, times(1)).removeApplication(activeApp, ApplicationState.KILLED)
  }

  test("Kill application by name") {
    val appDesc = createAppDesc()
    // use new start date so it isn't filtered by UI
    val activeApp = new ApplicationInfo(
      new Date().getTime, "app-0", appDesc, new Date(), null, Int.MaxValue)

    when(master.nameToApp).thenReturn(HashMap[String,
        ApplicationInfo]((activeApp.desc.name, activeApp)))

    val url = s"http://localhost:${masterWebUI.boundPort}/app/killByName/"
    val body = convPostDataToString(Map(("name", activeApp.desc.name), ("terminate", "true")))
    val conn = sendHttpRequest(url, "POST", body)
    conn.getResponseCode

    // Verify the master was called to remove the active app
    verify(master, times(1)).removeApplication(activeApp, ApplicationState.KILLED)
  }

  test("kill driver") {
    val activeDriverId = "driver-0"
    val url = s"http://localhost:${masterWebUI.boundPort}/driver/kill/"
    val body = convPostDataToString(Map(("id", activeDriverId), ("terminate", "true")))
    val conn = sendHttpRequest(url, "POST", body)
    conn.getResponseCode

    // Verify that master was asked to kill driver with the correct id
    verify(masterEndpointRef, times(1)).ask[KillDriverResponse](RequestKillDriver(activeDriverId))
  }

  private def convPostDataToString(data: Map[String, String]): String = {
    (for ((name, value) <- data) yield s"$name=$value").mkString("&")
  }

  /**
   * Send an HTTP request to the given URL using the method and the body specified.
   * Return the connection object.
   */
  private def sendHttpRequest(
      url: String,
      method: String,
      body: String = ""): HttpURLConnection = {
    val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod(method)
    if (body.nonEmpty) {
      conn.setDoOutput(true)
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      conn.setRequestProperty("Content-Length", Integer.toString(body.length))
      val out = new DataOutputStream(conn.getOutputStream)
      out.write(body.getBytes(StandardCharsets.UTF_8))
      out.close()
    }
    conn
  }
}
