/*
 * Copyright to the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.rioproject.config.Constants

/*
* Configuration properties for the Gnostic
*/
manager {
    String rioHome = System.getProperty("rio.home")
    StringBuilder classPath = new StringBuilder()
    File rioLib = new File(rioHome+'/lib/')
    for(File file : rioLib.listFiles()) {
        if(file.name.startsWith("rio-start")) {
            if(classPath.length()>0)
                classPath.append(File.pathSeparator)
            classPath.append(file.path)
        } else if(file.name.startsWith("groovy-all")) {
            if(classPath.length()>0)
                classPath.append(File.pathSeparator)
            classPath.append(file.path)
        }
    }

    classPath.append(File.pathSeparator).append(System.getProperty("JAVA_HOME")).append("/lib/tools.jar")
    execClassPath = classPath.toString()

    inheritOptions = true

    /* Get the directory that the logging FileHandler will create the service log.  */
    String opSys = System.getProperty('os.name')
    String rootLogDir = opSys.startsWith("Windows")?'${java.io.tmpdir}':'/tmp'
    String name = System.getProperty('user.name')

    log = "${rootLogDir}/${name}/logs"

    jvmOptions =
        '-Djava.protocol.handler.pkgs=org.rioproject.url '+
        '-Djava.rmi.server.useCodebaseOnly=false '+
        '-XX:+HeapDumpOnOutOfMemoryError -XX:+UseConcMarkSweepGC -XX:+AggressiveOpts -XX:HeapDumpPath=${rio.home}${/}logs '+
        '-server -Xms8m -Xmx256m -Djava.security.policy=${rio.home}${/}policy${/}policy.all '+
        '-Drio.home=${rio.home} -Drio.test.attach '+
        '-Dorg.rioproject.groups=${org.rioproject.groups} '+
        '-Dorg.rioproject.service=${service}'


    mainClass = 'org.rioproject.start.ServiceStarter'

    /*
    * Remove any previously created service log files
    */
    cleanLogs = true

    monitorStarter = '${user.dir}${/}src${/}test${/}conf${/}start-monitor.groovy'
    cybernodeStarter = '${rio.home}/config/start-cybernode.groovy'
}