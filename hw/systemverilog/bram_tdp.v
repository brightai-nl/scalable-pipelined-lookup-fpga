// A parameterized, inferable, true dual-port, dual-clock block RAM in Verilog.

module bram_tdp #(
  parameter STAGE_ID = 0,
  parameter DATA = 72,
  parameter ADDR = 10,
  parameter MEMINIT_DIR = "../gen/meminit/",
  parameter MEMINIT_FILENAME = "stage00.mem"
) (
  // Port A
  input   wire                a_clk,
  input   wire                a_wr,
  input   wire    [ADDR-1:0]  a_addr,
  input   wire    [DATA-1:0]  a_din,
  output  reg     [DATA-1:0]  a_dout,

  // Port B
  input   wire                b_clk,
  input   wire                b_wr,
  input   wire    [ADDR-1:0]  b_addr,
  input   wire    [DATA-1:0]  b_din,
  output  reg     [DATA-1:0]  b_dout
);

 `define INFERRED 1
 `ifdef INFERRED
// Shared memory
//(* ram_style = "block" *) reg [DATA-1:0] mem [(2**ADDR)-1:0];
(* ram_style = "ultra" *) reg [DATA-1:0] mem [(2**ADDR)-1:0];

localparam MEMINIT_PATH = {MEMINIT_DIR, MEMINIT_FILENAME};

initial begin
  $display("Initializing RAM for stage %0d with contents of %s.", STAGE_ID, MEMINIT_PATH);
  $readmemh(MEMINIT_PATH, mem);
end

// Port A
always @(posedge a_clk) begin
  a_dout      <= mem[a_addr];
  if (a_wr) begin
    //a_dout      <= a_din;
    mem[a_addr] <= a_din;
  end
end

// Port B
always @(posedge b_clk) begin
  b_dout      <= mem[b_addr];
  if (b_wr) begin
    //b_dout      <= b_din;
    mem[b_addr] <= b_din;
  end
end

`else // !INFERRED // Xilinx

//wire [DATA-1:0]  a_dout_w, b_dout_w;

  xpm_memory_tdpram #(
      .ADDR_WIDTH_A(ADDR),
      .ADDR_WIDTH_B(ADDR),
      .AUTO_SLEEP_TIME(0),
      .BYTE_WRITE_WIDTH_A(DATA),
      .BYTE_WRITE_WIDTH_B(DATA),
      .CASCADE_HEIGHT(0),
      .CLOCKING_MODE("common_clock"),
      .ECC_MODE("no_ecc"),
      .MEMORY_INIT_FILE(MEMINIT_FILENAME),
      .MEMORY_INIT_PARAM("0"),
      .MEMORY_OPTIMIZATION("true"),
      .MEMORY_PRIMITIVE("auto"),
      .MEMORY_SIZE(2**ADDR * DATA),
      .MESSAGE_CONTROL(0),
      .READ_DATA_WIDTH_A(DATA),
      .READ_DATA_WIDTH_B(DATA),
      .READ_LATENCY_A(1),
      .READ_LATENCY_B(1),
      .READ_RESET_VALUE_A("0"),
      .READ_RESET_VALUE_B("0"),
      .RST_MODE_A("SYNC"),
      .RST_MODE_B("SYNC"),
      .SIM_ASSERT_CHK(0),
      .USE_EMBEDDED_CONSTRAINT(0),
      .USE_MEM_INIT(1),
      .WAKEUP_TIME("disable_sleep"),
      .WRITE_DATA_WIDTH_A(DATA),
      .WRITE_DATA_WIDTH_B(DATA),
      .WRITE_MODE_A("read_first"),
      .WRITE_MODE_B("read_first") // read_first, no_change
   )
   xpm_memory_tdpram_inst (
      .dbiterra(),
      .dbiterrb(),
      .douta(a_dout),
      .doutb(b_dout),
      .sbiterra(),
      .sbiterrb(),
      .addra(a_addr),
      .addrb(b_addr),
      .clka(a_clk),
      .clkb(b_clk),
      .dina(a_din),
      .dinb(b_din),
      .ena(1'b1),
      .enb(1'b1),
      .injectdbiterra(0),
      .injectdbiterrb(0),
      .injectsbiterra(0),
      .injectsbiterrb(0),
      .regcea(1),
      .regceb(1),
      .rsta(0),
      .rstb(0),
      .sleep(0),
      .wea(1),
      .web(1)
   );

`endif

endmodule
