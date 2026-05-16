import { readFileSync } from "node:fs";

const csv = readFileSync("zefix_aargau.csv", "utf-8");
const rows = csv.split("\n").slice(1).filter(l => l);

const buckets = { "0-20": 0, "20-50": 0, "50-100": 0, "100-200": 0, "200+": 0 };
for (const line of rows) {
    // Quick & dirty: assume description is column 4
    const desc = line.split(",")[3] || "";
    const len = desc.length;
    if (len < 20) buckets["0-20"]++;
    else if (len < 50) buckets["20-50"]++;
    else if (len < 100) buckets["50-100"]++;
    else if (len < 200) buckets["100-200"]++;
    else buckets["200+"]++;
}
console.log(buckets);