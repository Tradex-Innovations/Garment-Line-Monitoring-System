import { useEffect, useRef } from "react";
import Chart from "chart.js/auto";

// ─── Data ─────────────────────────────────────────────────────────────────────
const mockData = [
  { time: "6:00",  workers: 0  },
  { time: "7:00",  workers: 12 },
  { time: "7:30",  workers: 35 },
  { time: "8:00",  workers: 67 },
  { time: "8:30",  workers: 82 },
  { time: "9:00",  workers: 85 },
  { time: "10:00", workers: 85 },
  { time: "11:00", workers: 83 },
  { time: "12:00", workers: 45 },
];

// ─── Component ────────────────────────────────────────────────────────────────
export function AttendanceChart() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const chartRef  = useRef<any>(null);

  useEffect(() => {
    if (!canvasRef.current) return;
    if (chartRef.current) chartRef.current.destroy();

    chartRef.current = new Chart(canvasRef.current, {
        type: "line",
        data: {
          labels: mockData.map((d) => d.time),
          datasets: [
            {
              label: "Workers",
              data: mockData.map((d) => d.workers),
              borderColor: "#2563EB",
              borderWidth: 2.5,
              pointBackgroundColor: "#2563EB",
              pointRadius: 4,
              pointHoverRadius: 6,
              fill: true,
              tension: 0.4,
              backgroundColor: (ctx) => {
                const chart = ctx.chart;
                const { ctx: c, chartArea } = chart;
                if (!chartArea) return "rgba(37,99,235,0.08)";
                const gradient = c.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
                gradient.addColorStop(0,   "rgba(37,99,235,0.22)");
                gradient.addColorStop(1,   "rgba(37,99,235,0)");
                return gradient;
              },
            },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: {
              backgroundColor: "#FFFFFF",
              borderColor: "#E2E4EA",
              borderWidth: 1,
              titleColor: "#8B90A0",
              bodyColor: "#111318",
              bodyFont: { family: "'DM Sans', system-ui, sans-serif", size: 12, weight: 600 },
              titleFont: { family: "'DM Sans', system-ui, sans-serif", size: 11 },
              padding: 10,
              cornerRadius: 10,
              callbacks: {
                label: (c) => ` ${c.parsed.y} workers`,
              },
            },
          },
          scales: {
            x: {
              grid: { display: false },
              border: { display: false },
              ticks: {
                font: { family: "'DM Sans', system-ui, sans-serif", size: 11 },
                color: "#8B90A0",
              },
            },
            y: {
              grid: { color: "#F0F1F4" },
              border: { display: false },
              min: 0,
              max: 100,
              ticks: {
                font: { family: "'DM Sans', system-ui, sans-serif", size: 11 },
                color: "#8B90A0",
                stepSize: 20,
              },
            },
          },
        },
      });

    return () => { if (chartRef.current) chartRef.current.destroy(); };
  }, []);

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600&display=swap');
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: 'DM Sans', system-ui, sans-serif; background: #F5F6F8; color: #111318; }
      `}</style>

      <div style={{ background: "#FFFFFF", border: "1px solid #E2E4EA", borderRadius: 14, padding: 18, fontFamily: "'DM Sans', system-ui, sans-serif" }}>
        {/* Header */}
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: "#111318" }}>Attendance Trend</div>
          <div style={{ fontSize: 11, color: "#8B90A0", marginTop: 2 }}>Worker check-ins throughout the day</div>
        </div>

        {/* Chart */}
        <div style={{ position: "relative", width: "100%", height: 280 }}>
          <canvas ref={canvasRef} />
        </div>
      </div>
    </>
  );
}

export default AttendanceChart;
