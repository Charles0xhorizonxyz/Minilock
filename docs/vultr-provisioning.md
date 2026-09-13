# Vultr build box

A GrapheneOS build needs far more machine than a laptop. This is a **rent-for-a-few-hours, then destroy** box.

## Don't use Block Storage

Vultr Block Storage is network-attached. An AOSP build is millions of small-file reads and writes, which is exactly the workload that suffers most over the network. Put the whole tree on the instance's **local NVMe** instead.

At 440 GB, Block Storage is also $44/month — more than the compute itself for a job measured in hours.

To keep the source between sessions, take an **instance snapshot** before destroying, rather than parking a volume.

## Prompt for the Vultr console agent

> Provision a temporary Linux build server for compiling AOSP/GrapheneOS. I will destroy it in a few hours, so optimise for build speed, not for cost over time.
>
> **Instance**
> - Optimized Cloud Compute — CPU-Optimized, or Storage-Optimized if that gives more local NVMe
> - At least **16 vCPU** (32 strongly preferred — this workload scales almost linearly with cores)
> - At least **64 GB RAM** (AOSP uses roughly 2 GB per parallel job)
> - At least **600 GB of local NVMe** on the instance itself
> - **Do not attach Block Storage.** The build must run on local NVMe.
> - OS: **Ubuntu 24.04 LTS**
> - Location: **Amsterdam, NL**
> - Label: `minilock-aosp-build`
> - Enable IPv6. Do not enable auto-backups — this machine is disposable.
>
> **Access**
> - Add this SSH public key and allow root login by key only:
>   `ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINjpSLjVlrVZp/0Se522JSBAJ8UVl4rGkE0ZQdim5+4w vultr`
> - Firewall group `minilock-build`: allow TCP 22 from my current IP only; deny everything else inbound.
>
> Then tell me the instance's **public IPv4 address**, the exact plan chosen, the local disk size, and the hourly rate.

## After it boots

```bash
ssh root@<IP>
```

Bootstrap (about 10 minutes):

```bash
apt update && apt full-upgrade -y
apt install -y git-core gnupg flex bison build-essential zip curl zlib1g-dev \
  libc6-dev-i386 libncurses5 lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc \
  unzip fontconfig python3 python3-pip rsync ccache openjdk-21-jdk signify-openbsd

# repo
mkdir -p ~/bin && curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
chmod a+x ~/bin/repo && echo 'export PATH=$HOME/bin:$PATH' >> ~/.bashrc

# ccache pays for itself from the second build onwards
export USE_CCACHE=1 CCACHE_EXEC=$(command -v ccache)
ccache -M 100G

df -h /          # confirm the local NVMe is where the tree will live
nproc && free -g
```

Then sync the GrapheneOS tree per <https://grapheneos.org/build>, targeting `panther` (Pixel 7).

Expect roughly **150 GB** of source and a similar amount of build output. Sync takes longer than most people expect — snapshot the instance once the sync completes, before building, so a future session skips it.

## How long we actually need it

On the chosen shape — `voc-c-32c-64gb-1000s`, 32 vCPU / 64 GB / 1000 GB NVMe at $0.986/hr:

| Phase | Time |
|---|---|
| Bootstrap: deps, `repo`, ccache, swapfile | ~15 min |
| `repo sync` of GrapheneOS (~150 GB) | **1.5–3 h** — by far the most variable |
| First clean build | 1.5–2.5 h |
| Generate keys, sign, build factory images | 30–45 min |
| Apply the keyguard patch, incremental rebuilds | 30–60 min |
| Download output | ~10 min |
| **First session, total** | **5–8 hours ≈ $5–8** |

Snapshot once the sync finishes. A later session restores it and skips straight to building: roughly **1.5 hours ≈ $1.50** per iteration after that.

### Two technical notes for this shape

- **64 GB with 32 cores is the standard ratio but the link steps spike.** Add a swapfile before building, and leave a couple of cores spare:

  ```bash
  fallocate -l 32G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  # then build with -j28 rather than -j32
  ```

- 1000 GB is comfortable: ~150 GB source, ~250 GB `out/`, ~100 GB ccache.

## Billing: what actually stops, and what doesn't

Vultr bills **hourly, capped at the monthly figure**. The $720/mo on the summary is only what you'd pay leaving it up for a whole month; eight hours is about $7.89.

**Powering the instance off does not stop billing. Only destroying it does.** This is the mistake that costs people real money.

Things that keep billing after the server is gone, unless you delete them too:

| Item | Watch for |
|---|---|
| **Snapshots** | Billed per GB per month and they survive instance destruction. A snapshot of a synced tree is large. Keep it only if another build is coming; delete it otherwise. |
| **Automatic Backups** | $144/mo on this plan. Leave **disabled** — the box is disposable. |
| **DDoS Protection** | $10/mo. Leave off. |
| **Reserved IP** | Bills on its own once reserved. Don't reserve one. |
| **Block Storage** | Not used here by design. Confirm none was created. |

Bandwidth is a non-issue: 10 TB included, and we pull ~150 GB in (inbound is typically free) and push ~2 GB out.

## Before destroying

1. Download the signed build output.
2. Download the **signing keys** and back them up properly. Losing them means the phone must be wiped to update it again. They must never be committed — `.gitignore` already excludes `keys/`, `*.pem`, `*.pk8`, `*.jks`.
3. Snapshot the instance only if another build is likely — it bills per GB per month.
4. **Destroy** the instance (not stop it), then check Billing → Usage and confirm nothing is still accruing.
