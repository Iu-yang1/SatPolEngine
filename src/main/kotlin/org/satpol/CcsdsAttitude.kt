package org.satpol

import java.time.Duration
import java.time.Instant
import kotlin.math.*

/**
 * Strict subset of CCSDS 504.0-B AEM KVN data.
 *
 * Only accepts UTC + BODY->ITRF/ECEF quaternion ephemerides with explicit
 * quaternion ordering FIRST/LAST. Never assumes an inertial quaternion is
 * Earth-fixed or that a spacecraft has a known antenna orientation.
 * AEM trajectory represents spacecraft attitude, NOT antenna RF polarization.
 */
class CcsdsAttitude private constructor(
    private val samples:List<Pair<Instant,Quaternion>>,
    val spacecraftId:String,
    val source:String,
    private val maximumGapSeconds:Double,
    private val validFrom:Instant,
    private val validUntil:Instant
): AttitudeAxisModel {
    private var antennaAxisBody:Vector3?=null
    private var antennaAxisProvenance:String?=null

    fun withCalibratedAntennaAxis(axis:Vector3, axisEvidence:String):CcsdsAttitude {
        require(axis.isFinite()&&axis.norm()>1e-10)
        require(axisEvidence.isNotBlank()) { "Satellite antenna mechanical axis source required" }
        antennaAxisBody=axis.unit()
        antennaAxisProvenance=axisEvidence
        return this
    }

    override fun axisEcef(state:OrbitStateEcef,time:Instant):AttitudeAxisEcef? {
        val axis=antennaAxisBody?:return null
        val q=quaternionAt(time)?:return null
        return AttitudeAxisEcef(q.rotate(axis).unit(),AttitudeEvidence.OBSERVED,
            "$source; antenna mounting: ${antennaAxisProvenance ?: error(\"Missing axis evidence\")}")
    }

    fun quaternionAt(time:Instant):Quaternion? {
        if(time<validFrom||time>validUntil)return null
        if(time<samples.first().first||time>samples.last().first)return null
        val i=samples.binarySearch { (epoch,_) -> epoch.compareTo(time) }
        if(i>=0) return samples[i].second
        val idx=-(i+1)
        val (at,a)=samples[idx-1];val (bt,b)=samples[idx]
        val dt=Duration.between(at,bt).toNanos()*1e-9
        if(dt<=0 ||dt>maximumGapSeconds)return null
        val fract=Duration.between(at,time).toNanos()*1e-9/dt
        return slerp(a,b,fract)
    }

    companion object {
        /** AEM requires explicit TIME_SYSTEM=UTC, SC_BODY->ITRF orientation. */
        fun parseAemKvn(
            raw:String,
            source:String,
            maximumGapSeconds:Double=120.0
        ):CcsdsAttitude {
            require(source.isNotBlank())
            require(maximumGapSeconds.isFinite()&&maximumGapSeconds>0)
            var inMeta=false
            var inData=false
            var segmentCount=0
            val meta=linkedMapOf<String,String>()
            val data=mutableListOf<Pair<Instant,Quaternion>>()
            raw.lineSequence().map(String::trim).filter{it.isNotEmpty()}.forEach{line ->
                when(line){
                    "META_START" -> { require(!inMeta&&!inData&&segmentCount==0)
                        inMeta=true;segmentCount++ }
                    "META_STOP" -> {require(inMeta);inMeta=false}
                    "DATA_START" -> {require(!inMeta&&!inData&&segmentCount==1);inData=true}
                    "DATA_STOP" -> {require(inData);inData=false}
                    else -> {
                        if(inMeta) {
                            val pos=line.indexOf('=')
                            require(pos>0) { "Malformed metadata" }
                            meta[line.substring(0,pos).trim()]=line.substring(pos+1).trim()
                        } else if(inData && !line.startsWith("COMMENT")) {
                            val a=line.split(Regex("\\s+"))
                            require(a.size==5) { "Only 4-component AEM quaternions supported" }
                            val first=meta["QUATERNION_TYPE"]=="FIRST"
                            val numbers=a.drop(1).map(String::toDouble)
                            require(numbers.all(Double::isFinite))
                            val q=if(first) Quaternion(numbers[0],numbers[1],numbers[2],numbers[3])
                                else Quaternion(numbers[3],numbers[0],numbers[1],numbers[2])
                            val n=sqrt(q.w*q.w+q.x*q.x+q.y*q.y+q.z*q.z)
                            require(n in 0.98..1.02) { "Unnormalized spacecraft quaternion" }
                            val normalized=Quaternion(q.w/n,q.x/n,q.y/n,q.z/n)
                            data+=Instant.parse(a[0]) to normalized
                        } else if(!line.startsWith("COMMENT")&&!line.contains('=')) {
                            error("Unexpected AEM data: $line")
                        }
                    }
                }
            }
            require(segmentCount==1&&!inMeta&&!inData)
            require(meta["TIME_SYSTEM"]=="UTC")
            require(meta["CENTER_NAME"]?.uppercase()=="EARTH")
            require(meta["REF_FRAME_A"]?.uppercase() in setOf("SC_BODY","SC_BODY_1"))
            require(meta["REF_FRAME_B"]?.uppercase() in setOf("ITRF","ITRF93","ECEF"))
            require(meta["ATTITUDE_TYPE"]=="QUATERNION")
            require(meta["QUATERNION_TYPE"] in setOf("FIRST","LAST"))
            val id=meta["OBJECT_ID"]?:error("AEM missing OBJECT_ID")
            require(id.isNotBlank())
            require(data.size>=2&&data.zipWithNext().all{(a,b)->a.first<b.first})
            val start=Instant.parse(meta["USEABLE_START_TIME"]?:meta["START_TIME"]
                ?:error("Missing start time"))
            val stop=Instant.parse(meta["USEABLE_STOP_TIME"]?:meta["STOP_TIME"]
                ?:error("Missing stop time"))
            require(start<=stop)
            return CcsdsAttitude(data,id,source,maximumGapSeconds,start,stop)
        }

        private fun slerp(a:Quaternion,b:Quaternion,t:Double):Quaternion {
            require(t in 0.0..1.0)
            var dot=a.w*b.w+a.x*b.x+a.y*b.y+a.z*b.z
            val other=if(dot<0){dot=-dot;Quaternion(-b.w,-b.x,-b.y,-b.z)}else b
            val sa:Double;val sb:Double
            if(dot>0.9995){sa=1.0-t;sb=t}
            else {
                val theta=acos(dot.coerceIn(-1.0,1.0));val st=sin(theta)
                sa=sin((1-t)*theta)/st;sb=sin(t*theta)/st
            }
            val q=Quaternion(a.w*sa+other.w*sb,a.x*sa+other.x*sb,
                a.y*sa+other.y*sb,a.z*sa+other.z*sb)
            val norm=sqrt(q.w*q.w+q.x*q.x+q.y*q.y+q.z*q.z)
            return Quaternion(q.w/norm,q.x/norm,q.y/norm,q.z/norm)
        }
    }
}
