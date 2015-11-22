LATTICE_TGZ_URL = "https://s3-us-west-2.amazonaws.com/lattice/lattice-tgz/lattice-v0.6.0-34-g32acfa9.tgz"
Vagrant.configure("2") do |config|
  config.vm.box = "lattice/colocated"
  config.vm.box_version = "0.1.0"

  config.vm.synced_folder ".", "/vagrant", disabled: true
  config.vm.provision "file", source: "lattice.tgz", destination: "/tmp/lattice.tgz"

  provider_is_aws = (!ARGV.nil? && ARGV.join(' ').match(/provider(=|\s+)aws/))

  if Vagrant.has_plugin?("vagrant-proxyconf") && !provider_is_aws
    config.proxy.http = ENV["http_proxy"]
    config.proxy.https = ENV["https_proxy"]
    config.proxy.no_proxy = [
      "localhost", "127.0.0.1",
      (ENV["LATTICE_IP"] || "192.168.11.11"),
      (ENV["LATTICE_DOMAIN"] || "192.168.11.11.xip.io"),
      ".consul"
    ].join(',')

    config.vm.provision "shell", inline: "grep -i proxy /etc/environment > /var/lattice/proxy || true"
  end

  # Source: https://stefanwrobel.com/how-to-make-vagrant-performance-not-suck
  config.vm.provider "virtualbox" do |v|
    host = RbConfig::CONFIG['host_os']
    if host =~ /darwin/
      cpus = `sysctl -n hw.ncpu`.to_i
      mem = `sysctl -n hw.memsize`.to_i / 1024 / 1024 / 4
    elsif host =~ /linux/
      cpus = `nproc`.to_i
      mem = `grep 'MemTotal' /proc/meminfo | sed -e 's/MemTotal://' -e 's/ kB//'`.to_i / 1024 / 4
    else
      cpus = 2
      mem = 2048
    end

    v.customize ["modifyvm", :id, "--memory", mem]
    v.customize ["modifyvm", :id, "--cpus", cpus]
    v.customize ["modifyvm", :id, "--ioapic", "on"]
  end

  config.vm.provider :aws do |aws, override|
    aws.access_key_id = ENV["AWS_ACCESS_KEY_ID"]
    aws.secret_access_key = ENV["AWS_SECRET_ACCESS_KEY"]
    aws.keypair_name = ENV["AWS_SSH_PRIVATE_KEY_NAME"]
    aws.region = ENV["AWS_REGION"] || 'us-east-1'
    aws.instance_type = "m4.large"
    aws.ebs_optimized = true
    aws.tags = { "Name" => (ENV["AWS_INSTANCE_NAME"] || "vagrant") }
    aws.ami = ""
    aws.block_device_mapping = [
      {
        "DeviceName" => "/dev/sda1",
        "Ebs.VolumeType" => "gp2",
        "Ebs.VolumeSize" => 15,
        "Ebs.DeleteOnTermination" => true
      }
    ]

    override.ssh.username = "ubuntu"
    override.ssh.private_key_path = ENV["AWS_SSH_PRIVATE_KEY_PATH"]
  end

  if provider_is_aws
    network_config = <<-SCRIPT
      public_ip="$(curl http://169.254.169.254/latest/meta-data/public-ipv4)"
      domain="#{ENV["LATTICE_DOMAIN"] || "${public_ip}.xip.io"}"
      garden_ip="$(ip route get 1 | awk '{print $NF;exit}')"
    SCRIPT
  else
    public_ip = ENV["LATTICE_IP"] || "192.168.11.11"
    default_domain = (public_ip == "192.168.11.11") ? "local.lattice.cf" : "#{public_ip}.xip.io"

    network_config = <<-SCRIPT
      domain="#{ENV["LATTICE_DOMAIN"] || default_domain}"
      garden_ip="#{public_ip}"
    SCRIPT

    config.vm.network "private_network", ip: public_ip
  end

  case RUBY_PLATFORM
  when /darwin/
    ltc_arch_path = 'osx/ltc'
  when /linux/i
    ltc_arch_path = 'linux/ltc'
  when /cygwin|mswin|mingw|bccwin|wince|emx/i
    ltc_arch_path = 'windows/ltc.exe'
  end

  config.vm.provision "shell" do |s|
    s.inline = <<-SCRIPT
      set -e
      #{network_config}

      echo "GARDEN_IP=$garden_ip" >> /var/lattice/setup
      echo "DOMAIN=$domain" >> /var/lattice/setup
      echo 'HOST_ID=lattice-colocated-0' >> /var/lattice/setup
      echo 'USERNAME=#{ENV["LATTICE_USERNAME"]}' >> /var/lattice/setup
      echo 'PASSWORD=#{ENV["LATTICE_PASSWORD"]}' >> /var/lattice/setup

      tar xzf /tmp/lattice.tgz -C /tmp install
      /tmp/install/common
      /tmp/install/brain /tmp/lattice.tgz
      /tmp/install/cell /tmp/lattice.tgz
      /tmp/install/start

      echo "Lattice is now installed and running."
      echo "----------------------------------------------------------------"
      echo "To obtain a version of ltc that is compatible with your cluster:"
      if [ "#{ltc_arch_path}" = "windows/ltc.exe" ]; then
        echo "Download http://receptor.$domain/v1/sync/#{ltc_arch_path}"
      else
        echo "$ curl -O http://receptor.$domain/v1/sync/#{ltc_arch_path}"
        echo "$ chmod +x ltc"
      fi
      echo "Optionally, move ltc to a directory in your PATH."
      echo "You may then target your cluster using: ltc target $domain"
      echo "----------------------------------------------------------------"
    SCRIPT
  end
end

require 'net/http'
require 'rubygems/package'
require 'uri'
require 'zlib'

provision_required = (!ARGV.nil? && ['up', 'provision', 'reload'].include?(ARGV[0]))
lattice_tgz = File.join(File.dirname(__FILE__), "lattice.tgz")
lattice_tgz_url = defined?(LATTICE_TGZ_URL) && LATTICE_TGZ_URL

def download_lattice_tgz(url)
  uri = URI(url)

  http_args = [uri.host, uri.port]
  proxy_url = ENV[(uri.scheme=='https' ? 'https_proxy' : 'http_proxy')]
  if proxy_url
    proxy_uri = URI(proxy_url)
    http_args << proxy_uri.host
    http_args << proxy_uri.port
  end

  Net::HTTP.start(*http_args, use_ssl: uri.scheme == 'https') do |http|
    http.request_get(uri) do |response|
      open('lattice.tgz', 'wb') do |file|
        response.read_body do |chunk|
          file.write(chunk)
          sleep(0.005)
        end
      end
    end
  end
end

def get_lattice_tgz_version(filename)
    Zlib::GzipReader.open(filename) do |gz|
      tr = Gem::Package::TarReader.new(gz)
      tr.seek 'brain/var/lattice/versions/LATTICE_RELEASE' do |entry|
        return entry.read.chomp
      end
    end
    return nil
end

if provision_required && File.exists?(lattice_tgz)
  tgz_version = get_lattice_tgz_version(lattice_tgz)
  if lattice_tgz_url
    url_version = lattice_tgz_url.match(/\/lattice-(v[^\/]+)\.tgz$/)[1]
    if tgz_version != url_version
      puts "Warning: lattice.tgz file version (#{tgz_version}) does not match Vagrantfile version (#{url_version})."
      puts 'Re-downloading and replacing local lattice.tgz...'
      download_lattice_tgz(lattice_tgz_url)
    end
  else
    repo_version = `git describe`.chomp
    if tgz_version != repo_version && ENV['IGNORE_VERSION_MISMATCH'] != "true"
      puts <<-EOM.gsub(/^ +/, '')
      *******************************************************************************
      Error: lattice.tgz #{tgz_version} != current commit #{repo_version}

      The lattice.tgz file was built using a different commit than the current one.
      To ignore this error, set IGNORE_VERSION_MISMATCH=true in your environment.

      NOTE: As of v0.4.0, the process for deploying Lattice via Vagrant has changed.
      Please use the process documented here:
      \thttp://github.com/cloudfoundry-incubator/lattice#launching-with-vagrant
      *******************************************************************************
      EOM
      exit(1)
    end
  end
end

if provision_required && !File.exists?(lattice_tgz)
  if lattice_tgz_url
    puts 'Local lattice.tgz not found, downloading...'
    download_lattice_tgz(lattice_tgz_url)
  else
    puts <<-EOM.gsub(/^ +/, '')
    *******************************************************************************
    Could not determine Lattice version, and no local lattice.tgz present.

    NOTE: As of v0.4.0, the process for deploying Lattice via Vagrant has changed.
    Please use the process documented here:
    \thttp://github.com/cloudfoundry-incubator/lattice#launching-with-vagrant
    *******************************************************************************
    EOM
    exit(1)
  end
end
