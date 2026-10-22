import { describe, expect, it, vi } from "vitest";

import { singleFlight } from "@/lib/single-flight";

describe("singleFlight", () => {
  it("진행 중에 다시 호출하면 실행하지 않고 같은 Promise를 돌려준다", async () => {
    const fn = vi.fn(() => Promise.resolve("ok"));
    const run = singleFlight(fn);

    const first = run();
    const second = run();

    expect(second).toBe(first);
    await expect(Promise.all([first, second])).resolves.toEqual(["ok", "ok"]);
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("성공으로 끝나면 다음 호출은 새로 실행한다", async () => {
    const fn = vi.fn(() => Promise.resolve("ok"));
    const run = singleFlight(fn);

    await run();
    await run();

    expect(fn).toHaveBeenCalledTimes(2);
  });

  it("거절로 끝나도 모든 호출자가 같은 거절을 받고 다음 호출은 새로 실행한다", async () => {
    const fn = vi.fn(() => Promise.reject(new Error("fail")));
    const run = singleFlight(fn);

    const results = await Promise.allSettled([run(), run()]);

    expect(results.map((result) => result.status)).toEqual(["rejected", "rejected"]);
    expect(fn).toHaveBeenCalledTimes(1);
    await expect(run()).rejects.toThrow("fail");
    expect(fn).toHaveBeenCalledTimes(2);
  });
});
