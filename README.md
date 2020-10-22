# apis-ccc

## Introduction
apis-cccは運用保守等を目的として電力融通に関わる情報をサーバ等の外部Serviceにアップロードし、  
各ノードの設定ファイルをサーバからダウンロードするクライアントソフトウェアである。  
apis-cccはEthernet等のコミュニケーションラインに接続された複数のノード上のapis-mainから情報を  
取得するためにVert.x, Hazelcastのフレームワーク機能を用いてクラスタを構築する。  
そしてクラスタ内に存在するGrid Masterから全ノードのハードウェア情報、apis-mainに関係する  
ソフトウェア情報、ノード固有情報等を取得し、任意のノードから電力融通情報を取得する。  


![キャプチャ](https://user-images.githubusercontent.com/71874910/95824505-5f35fc00-0d6a-11eb-8eb5-90fe79539d8a.PNG)


## Installation
Here is how to install apis-ccc individually.  
git, maven, groovy and JDK must be installed in advance.

```bash
$ git clone https://github.com/SonyCSL/apis-bom.git
$ cd apis-bom
$ mvn install
$ cd ../
$ git clone https://github.com/SonyCSL/apis-common.git
$ cd apis-common
$ mvn install
$ cd ../
$ git cone https://github.com/SonyCSL/apis-ccc.git
$ cd apis-ccc
$ mvn package
```

## Running
Here is how to run apis-ccc individually.  
```bash
$ cd exe
$ bash start.sh
```

## Stopping
Here is how to stop apis-ccc individually.  
```bash
$ cd exe
$ bash stop.sh
```

## Parameter Setting
Set the following file parameters in the exe folder as necessary.   
Refer to "Chapter 7, About Configuration Files" in the [apis-ccc_specification](#anchor1) for more information.

&emsp;config.json   
&emsp;&emsp;&emsp;- communityId   &emsp;(default : oss_communityId)  
&emsp;&emsp;&emsp;- clusterId     &emsp;(default : oss_clusterId)  

&emsp;cluster.xml  
&emsp;&emsp;&emsp;- \<member\>  &emsp;(default : 127.0.0.1)  
&emsp;&emsp;&emsp;- \<interface\>  &emsp;(default : 127.0.0.1)  

&emsp;start.sh  
&emsp;&emsp;&emsp;-conf &emsp; (default : ./config.json)  
&emsp;&emsp;&emsp;-cluster-host &emsp; (default : 127.0.0.1)    



<a id="anchor1"></a>
## Documentation



## License
&emsp;[Apache License Version 2.0](https://github.com/SonyCSL/apis-ccc/blob/master/LICENSE)


## Notice
&emsp;[Notice](https://github.com/SonyCSL/apis-ccc/blob/master/NOTICE.md)
