package scalablePipelinedLookup

import scala.io.AnsiColor

import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba4.axilite.sim.AxiLite4Driver

object LookupTopSim extends App {
  Config.sim.compile(LookupTop()).doSim { dut =>
    // Fork a process to generate the reset and the clock on the dut
    dut.clockDomain.forkStimulus(period = 10)
    val axiDriver = new AxiLite4Driver(dut.io.axi, dut.clockDomain)

    /** Update request. */
    def update(
        ipAddr: Long,
        length: Int,
        stageId: Int,
        location: Int,
        result: Int,
        childStageId: Int,
        childLocation: Int,
        childHasLeft: Boolean,
        childHasRight: Boolean
    ): Unit = {
      SpinalInfo(
        "Requesting update for "
          + f"IP=0x$ipAddr%x/$length, result=$result @stage=$stageId, loc=0x$location%x "
          + f"with child(s) @stage=$childStageId, loc=0x$childLocation%x, "
          + s"l/r=$childHasLeft/$childHasRight."
      )

      assert(
        axiDriver.read(dut.AxiAddress.UPDATE_STATUS) == 0,
        "Update cannot be pending before starting a new one."
      )
      // @TODO verify, but seems like AxiLite4Driver write() does not assert b.ready
      dut.io.axi.b.ready #= true
      axiDriver.write(dut.AxiAddress.UPDATE_ADDR, (location << 16) | stageId)
      axiDriver.write(dut.AxiAddress.UPDATE_PREFIX, ipAddr)
      axiDriver.write(dut.AxiAddress.UPDATE_PREFIX_INFO, length)
      axiDriver.write(
        dut.AxiAddress.UPDATE_CHILD,
        (childHasLeft.toInt << 25) | (childHasRight.toInt << 24) | (childLocation << 8) | childStageId
      )
      axiDriver.write(dut.AxiAddress.UPDATE_RESULT, result)

      axiDriver.write(dut.AxiAddress.UPDATE_COMMAND, 0)
      //dut.io.axi.b.ready #= false
    }

    SpinalInfo("TEST 1: See if lookup blocks update.")

    // Block the update by requesting lookup.
    dut.io.lookup.foreach(lookup => {
      lookup.payload #= 0x327b23c0L
      lookup.valid #= true
    })
    dut.clockDomain.waitSampling()

    // Try to update.
    update(0x327b23c0L, 28, 3, 1, 10/*result*/, 0, 0, true, false)
    assert(
      axiDriver.read(dut.AxiAddress.UPDATE_STATUS) == 1,
      "Update should be blocked during lookup."
    )

    // Unblock the update.
    dut.io.lookup.foreach(lookup => {
      lookup.payload #= 0
      lookup.valid #= false
    })

    SpinalInfo("TEST 2: Lookup on both channels.")

    /** Latency from lookup request to result. */
    val Latency = 1 + dut.config.ipAddrWidth * 2 + 1

    /** Cycle on which a lookup will be performed. */
    val LookupCycle = 1

    /** Count of cycles to simulate. */
    val Cycles = Latency + LookupCycle + 2

    /** Request IP cache used for result check.
      *
      * First is the IP address which is looked up. Second is the information if
      * it is valid.
      */
    val requestIpCache = Array.fill(2)(Array.fill(Cycles)(BigInt(0), false))

    for (i <- 0 until Cycles) {
      // Set defaults.
      dut.io.lookup.foreach(lookup => {
        lookup.payload #= 0
        lookup.valid #= false
      })

      if (i == LookupCycle) {
        // Request lookup on both channels.
        dut.io.lookup(0).valid #= true
        dut.io.lookup(0).payload #= 0x327b23c0L

        dut.io.lookup(1).valid #= true
        dut.io.lookup(1).payload #= 0x327b23c0L// 0x62555800L
      }


      // Populate lookup cache.
      for ((cache, dutLookup) <- requestIpCache zip dut.io.lookup) {
        cache(i) = (dutLookup.payload.toBigInt, dutLookup.valid.toBoolean)
      }

      // Present lookup result with latency taken into account.
      if (i >= Latency) {
        val inOut = requestIpCache
          .zip(dut.io.result)
          .map { case (cache, result) =>
            ((if (cache(i - Latency + 1)._2) AnsiColor.GREEN else AnsiColor.RED)
              + f"0x${cache(i - Latency + 1)._1}%08x -> "
              + f"result=0x${result.lookupResult.toInt}%08x "
              + AnsiColor.RESET)
          }
        println(s"Cycle $i: ${inOut(0)}, ${inOut(1)}")
      }

      dut.clockDomain.waitSampling()
    }
  }
}
