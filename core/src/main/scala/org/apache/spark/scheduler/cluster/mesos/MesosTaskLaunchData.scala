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

package org.apache.spark.scheduler.cluster.mesos

import java.nio.ByteBuffer

import org.apache.mesos.protobuf.ByteString

import org.apache.spark.internal.Logging
import org.apache.spark.scheduler.Task

/**
 * Wrapper for serializing the data sent when launching Mesos tasks.
 */
private[spark] case class MesosTaskLaunchData(
  serializedTask: ByteBuffer,
  taskData: Array[Byte],
  attemptNumber: Int) extends Logging {

  def toByteString: ByteString = {
    val dataBuffer = ByteBuffer.allocate(4 + serializedTask.limit + taskData.length)
    dataBuffer.putInt(attemptNumber)
    val dataLen = taskData.length
    dataBuffer.putInt(dataLen)
    if (dataLen > 0) {
      dataBuffer.put(taskData)
    }
    dataBuffer.put(serializedTask)
    dataBuffer.rewind
    logDebug(s"ByteBuffer size: [${dataBuffer.remaining}]")
    ByteString.copyFrom(dataBuffer)
  }
}

private[spark] object MesosTaskLaunchData extends Logging {
  def fromByteString(byteString: ByteString): MesosTaskLaunchData = {
    val byteBuffer = byteString.asReadOnlyByteBuffer()
    logDebug(s"ByteBuffer size: [${byteBuffer.remaining}]")
    val attemptNumber = byteBuffer.getInt // updates the position by 4 bytes
    val dataLen = byteBuffer.getInt
    val taskData = if (dataLen > 0) {
      val taskData = new Array[Byte](dataLen)
      byteBuffer.get(taskData)
      taskData
    } else Task.EMPTY
    val serializedTask = byteBuffer.slice() // subsequence starting at the current position
    MesosTaskLaunchData(serializedTask, taskData, attemptNumber)
  }
}
