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

## Before destroying

1. Download the signed build output.
2. Download the **signing keys** and back them up properly. Losing them means the phone must be wiped to update it again. They must never be committed — `.gitignore` already excludes `keys/`, `*.pem`, `*.pk8`, `*.jks`.
3. Snapshot the instance if another build is likely.
4. Destroy the instance *and* confirm no Block Storage or reserved IP is left billing.
