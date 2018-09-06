/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionml.core.spark

import com.actionml.core.store.backends.MongoStorage
import com.mongodb.MongoClient
import com.mongodb.client.MongoDatabase
import com.mongodb.spark.config.ReadConfig
import com.mongodb.spark.{MongoClientFactory, MongoConnector, MongoSpark}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.bson.codecs.configuration.CodecProvider

import scala.reflect.ClassTag

// todo: these should be put in the DAO as a mixin trait for Spark, in which case the params are all known or can be found
// leaving only the sc to be passed in perhaps implicitly
trait SparkStoreSupport {
  def readRdd[T](
    sc: SparkContext,
    codecs: List[CodecProvider],
    dbName: Option[String] = None,
    collectionName: Option[String] = None)
    (implicit ct: ClassTag[T]): RDD[T]
}

trait SparkMongoSupport extends SparkStoreSupport {

  override def readRdd[T](
    sc: SparkContext,
    codecs: List[CodecProvider] = List.empty,
    dbName: Option[String] = None,
    colName: Option[String] = None)
    (implicit ct: ClassTag[T]): RDD[T] = {
    if(dbName.isDefined && colName.isDefined) {
      // not sure if the codecs are understood here--I bet not
      val rc = ReadConfig(databaseName = dbName.get, collectionName = colName.get)
      MongoSpark
        .builder()
        .sparkContext(sc)
        .readConfig(rc)
        .connector(new GenericMongoConnector(codecs, ct))
        .build
        .toRDD()
    } else {
      MongoSpark
        .builder()
        .sparkContext(sc)
        .connector(new GenericMongoConnector(codecs, ct))
        .build
        .toRDD()
    }
  }
}

class GenericMongoConnector[T](@transient codecs: List[CodecProvider], @transient ct: ClassTag[T])
  extends MongoConnector(new GenericMongoClientFactory(codecs, ct))
    with Serializable {}

class GenericMongoClientFactory[T](@transient codecs: List[CodecProvider], @transient ct: ClassTag[T]) extends MongoClientFactory {
  override def create(): MongoClient = new GenericMongoClient[T](codecs, ct)
}

class GenericMongoClient[T](@transient codecs: List[CodecProvider], @transient ct: ClassTag[T]) extends MongoClient {

  override def getDatabase(databaseName: String): MongoDatabase =
    super.getDatabase(databaseName).withCodecRegistry(MongoStorage.codecRegistry(codecs)(ct))
}